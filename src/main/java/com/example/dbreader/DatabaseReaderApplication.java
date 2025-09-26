package com.example.dbreader;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

// Gerekli tüm Spring Boot, JPA, Zookeeper ve yardımcı kütüphanelerin import'ları
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Bu sınıf uygulamanın ana giriş noktasıdır.
// @SpringBootApplication: Spring Boot uygulamasının ana yapılandırmasını sağlar.
// @EnableScheduling: Zamanlanmış görevlerin (scheduled tasks) çalışmasını etkinleştirir.
// CommandLineRunner: Uygulama başladıktan hemen sonra çalışacak kodları tanımlar.
@SpringBootApplication
@EnableScheduling
public class DatabaseReaderApplication implements CommandLineRunner {

    // ZooKeeperLeaderElection sınıfının bir örneğini enjekte eder (Autowired).
    @Autowired
    private ZooKeeperLeaderElection leaderElection;

    // Uygulamayı başlatan ana metot.
    public static void main(String[] args) {
        SpringApplication.run(DatabaseReaderApplication.class, args);
    }

    // Uygulama başlatıldığında çalışacak metot.
    @Override
    public void run(String... args) throws Exception {
        // Liderlik seçim sürecini başlatan metodu çağırır.
        leaderElection.start();

        // Uygulama çalışmaya devam ederken ana thread'i bloke etmek için bir kilit objesi kullanıyoruz.
        // Liderlik seçimi ve periyodik işlemlerin çalışması için uygulama ayakta kalmalıdır.
        Object lock = new Object();

        // Uygulama kapatıldığında (örneğin Ctrl+C ile) temiz bir şekilde kapanma işlemi için bir "shutdown hook" eklenir.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Liderlik seçim servisini durdurur.
                leaderElection.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // Ana thread'i, kapatma kancası (shutdown hook) tetiklenene kadar bloke et.
        synchronized (lock) {
        lock.wait();
    }
    }
}

// Veritabanındaki 'data_records' tablosuna karşılık gelen JPA varlığı (entity).
// Bu sınıf, tablodaki her bir satırı bir Java nesnesi olarak temsil eder.
@Entity
@Table(name = "data_records")
class DataRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Birincil anahtar (Primary Key).

    @Column(name = "message")
    private String message; // Kaydedilen veri mesajı.

    @Column(name = "created_at")
    private LocalDateTime createdAt; // Kaydın oluşturulma zamanı.

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // Kaydın işlenme zamanı. Eğer null ise, kayıt işlenmemiştir.

    @Column(name = "processed_by")
    private String processedBy; // Kaydı işleyen uygulamanın ID'si.

    // Lombok kullanılmadığı için getter ve setter metotları manuel olarak yazılmıştır.
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
}

// Bu bir JPA Repository arayüzüdür.
// @Repository: Spring'e bu sınıfın veri erişim katmanı (DAO) olduğunu belirtir.
// JpaRepository: Veritabanı işlemleri için hazır metotlar (örneğin findById, save, findAll) sunar.
interface DataRecordRepository extends JpaRepository<DataRecord, Long> {
    // Özel bir sorgu metodu: Henüz işlenmemiş (processedAt alanı null olan) kayıtları bulur.
    List<DataRecord> findByProcessedAtIsNull();
}

// Bu sınıf, veritabanından veri okuma ve işleme mantığını içerir.
// @Service: Spring'e bu sınıfın bir iş mantığı servisi olduğunu belirtir.
class DataProcessor {

    // DataRecordRepository'yi enjekte eder. Veritabanı işlemleri için kullanılır.
    @Autowired
    private DataRecordRepository repository;

    // ZooKeeperLeaderElection servisini enjekte eder. Liderlik durumunu kontrol etmek için kullanılır.
    @Autowired
    private ZooKeeperLeaderElection leaderElection;

    private String instanceId; // Uygulamanın çalıştığı makinenin host adını tutar.

    // Yapıcı metot (constructor). Uygulama örneği için benzersiz bir kimlik (ID) oluşturur.
    public DataProcessor() {
        try {
            // Makinenin host adını alır, böylece loglarda hangi uygulamanın çalıştığı anlaşılır.
            this.instanceId = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // Eğer host adı alınamazsa, yedek bir ID oluşturur.
            this.instanceId = "unknown-" + System.currentTimeMillis();
        }
    }

    // Bu metot, belirlenen aralıklarla otomatik olarak çalıştırılır.
    // @Scheduled: Zamanlanmış görev olduğunu belirtir.
    // fixedDelayString: İki işlem arasındaki gecikmeyi milisaniye cinsinden ayarlar.
    // app.processing.interval: Uygulama ayarlarından (örneğin application.properties) gelen bir değerdir.
    // :60000: Eğer değer bulunamazsa varsayılan olarak 60000 ms (1 dakika) kullanır.
    @Scheduled(fixedDelayString = "${app.processing.interval:60000}")
    public void processRecords() {
        // Liderlik durumunu kontrol eder. Sadece lider olan uygulama bu bloğa girer.
        if (!leaderElection.isLeader()) {
            return; // Lider değilse metottan çıkar ve işlem yapmaz.
        }

        // Henüz işlenmemiş kayıtları veritabanından çeker.
        List<DataRecord> unprocessedRecords = repository.findByProcessedAtIsNull();

        if (!unprocessedRecords.isEmpty()) {
            // İşlenecek kayıt varsa loglara bilgi yazar.
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("🔥 [MASTER-" + instanceId + "] " + timestamp + " - Processing " + unprocessedRecords.size() + " records:");

            // Her bir kaydı döngüde işler.
            for (DataRecord record : unprocessedRecords) {
                // Kaydın işlenme zamanını ve işleyen uygulamayı günceller.
                record.setProcessedAt(LocalDateTime.now());
                record.setProcessedBy(instanceId);
                // Güncellenmiş kaydı veritabanına kaydeder.
                repository.save(record);

                // İşlenen kaydın logunu yazar.
                System.out.println("   ✅ Processed record ID: " + record.getId() + " - Message: " + record.getMessage());
            }
        } else {
            // İşlenmemiş kayıt yoksa loglara bilgi yazar.
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("📊 [MASTER-" + instanceId + "] " + timestamp + " - No unprocessed records found");
        }
    }
}

// Bu sınıf, Zookeeper'ın **Apache Curator** kütüphanesi ile liderlik seçimi mantığını uygular.
// @Component: Spring'e bu sınıfın bir bileşen olduğunu belirtir, böylece enjekte edilebilir.
@Component
class ZooKeeperLeaderElection {

    // Zookeeper bağlantı dizesini uygulama ayarlarından alır.
    // Varsayılan olarak "localhost:2181" kullanır.
    @Value("${zookeeper.connection-string:localhost:2181}")
    private String zookeeperConnectionString;

    private CuratorFramework client; // Zookeeper istemcisini yöneten ana nesne.
    private LeaderSelector leaderSelector; // Liderlik seçim mantığını yöneten nesne.
    // Liderlik durumunu atomik olarak tutan bayrak. Çoklu iş parçacığı güvenliği için kullanılır.
    private AtomicBoolean isLeader = new AtomicBoolean(false);
    private String instanceId; // Uygulama örneğinin benzersiz kimliği.

    // Yapıcı metot. Uygulama örneği için benzersiz bir kimlik (ID) oluşturur.
    public ZooKeeperLeaderElection() {
        try {
            this.instanceId = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            this.instanceId = "unknown-" + System.currentTimeMillis();
        }
    }

    // Liderlik seçim sürecini başlatan metot.
    public void start() throws Exception {
        // Zookeeper istemcisini oluşturur ve bağlanır.
        // ExponentialBackoffRetry: Bağlantı kesilmesi durumunda üstel geri çekilmeyle yeniden deneme stratejisi.
        client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();
        client.blockUntilConnected(); // Bağlantı kurulana kadar bekler.

        // LeaderSelector nesnesini oluşturur.
        // /dbreader/leader: Zookeeper'da liderlik seçiminin yapılacağı yol.
        // LeaderSelectorListener: Liderlik durumu değiştiğinde çağrılacak geri çağırma (callback) metotlarını içerir.
        leaderSelector = new LeaderSelector(client, "/dbreader/leader", new LeaderSelectorListener() {
            // Bu metot, bu örnek lider seçildiğinde çağrılır.
            @Override
            public void takeLeadership(CuratorFramework client) throws Exception {
                isLeader.set(true); // isLeader bayrağını true yapar.
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.println("👑 [" + timestamp + "] Instance " + instanceId + " became MASTER!");

                try {
                    // Lider olduğu sürece sonsuz bir döngüde bekler.
                    while (isLeader.get()) {
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // Liderlik kaybedildiğinde bu blok çalışır.
                    isLeader.set(false); // isLeader bayrağını false yapar.
                    String endTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    System.out.println("💔 [" + endTimestamp + "] Instance " + instanceId + " lost leadership");
                }
            }

            // Bu metot, Zookeeper bağlantı durumu değiştiğinde çağrılır.
            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                // Eğer bağlantı koptuysa veya askıya alındıysa, liderlik durumunu false yapar.
                if (newState == ConnectionState.LOST || newState == ConnectionState.SUSPENDED) {
                    isLeader.set(false);
                }
            }
        });

        // Eğer liderlik kaybedilirse, tekrar liderlik sırasına girmesini sağlar.
        leaderSelector.autoRequeue();
        // Liderlik seçim sürecini başlatır.
        leaderSelector.start();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("🚀 [" + timestamp + "] Instance " + instanceId + " started and joined leader election");
    }

    // Liderlik seçim sürecini ve Zookeeper bağlantısını durduran metot.
    public void stop() throws Exception {
        isLeader.set(false);
        if (leaderSelector != null) {
            leaderSelector.close();
        }
        if (client != null) {
            client.close();
        }
    }

    // Bu uygulamanın lider olup olmadığını kontrol eden metot.
    public boolean isLeader() {
        return isLeader.get();
    }
}
