package com.example.dbreader;

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
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.persistence.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@EnableScheduling
public class DatabaseReaderApplication implements CommandLineRunner {

    @Autowired
    private ZooKeeperLeaderElection leaderElection;

    public static void main(String[] args) {
        SpringApplication.run(DatabaseReaderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        leaderElection.start();
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                leaderElection.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}

@Entity
@Table(name = "data_records")
class DataRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "message")
    private String message;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "processed_by")
    private String processedBy;

    // Getters and Setters
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

@Repository
interface DataRecordRepository extends JpaRepository<DataRecord, Long> {
    List<DataRecord> findByProcessedAtIsNull();
}

@Service
class DataProcessor {
    
    @Autowired
    private DataRecordRepository repository;
    
    @Autowired
    private ZooKeeperLeaderElection leaderElection;
    
    private String instanceId;
    
    public DataProcessor() {
        try {
            this.instanceId = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            this.instanceId = "unknown-" + System.currentTimeMillis();
        }
    }
    
    @Scheduled(fixedDelayString = "${app.processing.interval:60000}")
    public void processRecords() {
        if (!leaderElection.isLeader()) {
            return; // Sadece master işlem yapar
        }
        
        List<DataRecord> unprocessedRecords = repository.findByProcessedAtIsNull();
        
        if (!unprocessedRecords.isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("🔥 [MASTER-" + instanceId + "] " + timestamp + " - Processing " + unprocessedRecords.size() + " records:");
            
            for (DataRecord record : unprocessedRecords) {
                record.setProcessedAt(LocalDateTime.now());
                record.setProcessedBy(instanceId);
                repository.save(record);
                
                System.out.println("   ✅ Processed record ID: " + record.getId() + " - Message: " + record.getMessage());
            }
        } else {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("📊 [MASTER-" + instanceId + "] " + timestamp + " - No unprocessed records found");
        }
    }
}

@Component
class ZooKeeperLeaderElection {
    
    @Value("${zookeeper.connection-string:localhost:2181}")
    private String zookeeperConnectionString;
    
    private CuratorFramework client;
    private LeaderSelector leaderSelector;
    private AtomicBoolean isLeader = new AtomicBoolean(false);
    private String instanceId;
    
    public ZooKeeperLeaderElection() {
        try {
            this.instanceId = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            this.instanceId = "unknown-" + System.currentTimeMillis();
        }
    }
    
    public void start() throws Exception {
        client = CuratorFrameworkFactory.newClient(zookeeperConnectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();
        client.blockUntilConnected();
        
        leaderSelector = new LeaderSelector(client, "/dbreader/leader", new LeaderSelectorListener() {
            @Override
            public void takeLeadership(CuratorFramework client) throws Exception {
                isLeader.set(true);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.println("👑 [" + timestamp + "] Instance " + instanceId + " became MASTER!");
                
                try {
                    // Master olduğu sürece bekle
                    while (isLeader.get()) {
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    isLeader.set(false);
                    String endTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    System.out.println("💔 [" + endTimestamp + "] Instance " + instanceId + " lost leadership");
                }
            }
            
            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                if (newState == ConnectionState.LOST || newState == ConnectionState.SUSPENDED) {
                    isLeader.set(false);
                }
            }
        });
        
        leaderSelector.autoRequeue();
        leaderSelector.start();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("🚀 [" + timestamp + "] Instance " + instanceId + " started and joined leader election");
    }
    
    public void stop() throws Exception {
        isLeader.set(false);
        if (leaderSelector != null) {
            leaderSelector.close();
        }
        if (client != null) {
            client.close();
        }
    }
    
    public boolean isLeader() {
        return isLeader.get();
    }
}