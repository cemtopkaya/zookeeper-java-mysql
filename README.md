#### Proje Gereksinimleri

- Java 17 gerekli
- Docker & Docker Compose gerekli
- Port 2181 (ZooKeeper), 3306 (MySQL) boş olmalı
- ./build.sh ve ./setup.sh executable yapmayı unutma
- Environment variables .env dosyasında

#### Hızlı Başlatma

```sh
# 1. Derle ve docker build öncesi artifaktı oluştur:
mvn clean package -DskipTests

# 2. Test ortamını için uygulama kopya konteynerlerini ayaklandır ve MySQL'i başlat:
docker compose -f docker-compose-test.yml up --build -d zookeeper mysql dbreader-app-1 dbreader-app-2 dbreader-app-3

# 2. Logları izle:
docker compose -f docker-compose logs  -f

# 3. Master'ı gör:
docker compose -f docker-compose logs | grep "became MASTER"

# 4. Test verisi ekle:
docker exec -it mysql mysql -u root -prootpassword dbreader_db -e "
INSERT INTO data_records (message) VALUES ('Test $(date)');"

# 5. İşlenen kayıtları kontrol et:
docker exec -it mysql mysql -u root -prootpassword dbreader_db -e "
SELECT * FROM data_records WHERE processed_at IS NOT NULL ORDER BY processed_at DESC LIMIT 5;"
```

**Test ortamını kapat**

```sh
docker compose -f docker-compose-test.yml down zookeeper mysql dbreader-app-1 dbreader-app-2 dbreader-app-3
```

##### Çıktılar

Çıktıları çalıştırmak yerine incelemek isteyenler için ekliyorum:

```shell
root@cf5055de26cf:/Users/cemt/projects/cemt-repos/zookeeper-java# docker compose -f docker-compose logs | grep "became MASTER"
dbreader-app-1      | 👑 [2025-09-24 00:56:23] Instance dbreader-app-1 became MASTER!
```

**Yeni bir kayıt ekleyelim ve MASTER'ın işlediğini görelim:**

Belirli bir periyotla veri okuduğu için aradaki satırlarda göreceksiniz MASTER'ın (`dbreader-app-1`) kaydı okuduğunu.

Aşağıdaki komutla yeni kayıt ekliyoruz ve ....

```shell
docker exec -it mysql mysql -u root -prootpassword dbreader_db -e "
INSERT INTO data_records (message) VALUES ('Test $(date)');"
```

...günlüklere düşmesini bekliyoruz:

```shell
zookeeper           | [2025-09-24 01:02:09,872] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:44026, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 01:02:19,967] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:51970, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 01:02:30,030] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:45196, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-1      | 🔥 [MASTER-dbreader-app-1] 2025-09-24 01:02:33 - Processing 1 records:
dbreader-app-1      |    ✅ Processed record ID: 6 - Message: Test Wed Sep 24 01:02:05 UTC 2025
zookeeper           | [2025-09-24 01:02:40,102] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:34242, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
```

**Master'ı durduralım ve yeni MASTER'ı görelim:**

```shell
dbreader-app-1      | 💔 [2025-09-24 00:58:15] Instance dbreader-app-1 lost leadership
dbreader-app-1      | 2025-09-24 00:58:15.741  INFO 1 --- [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
dbreader-app-1      | 2025-09-24 00:58:15.742 ERROR 1 --- [eaderSelector-0] o.a.c.f.recipes.leader.LeaderSelector    : The leader threw an exception
dbreader-app-1      |
dbreader-app-1      | java.lang.IllegalStateException: Expected state [STARTED] was [STOPPED]
dbreader-app-1      |   at org.apache.curator.shaded.com.google.common.base.Preconditions.checkState(Preconditions.java:821) ~[curator-client-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.imps.CuratorFrameworkImpl.checkState(CuratorFrameworkImpl.java:457) ~[curator-framework-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.imps.CuratorFrameworkImpl.delete(CuratorFrameworkImpl.java:477) ~[curator-framework-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.locks.LockInternals.deleteOurPath(LockInternals.java:347) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.locks.LockInternals.releaseLock(LockInternals.java:124) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.locks.InterProcessMutex.release(InterProcessMutex.java:154) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.doWork(LeaderSelector.java:454) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.doWorkLoop(LeaderSelector.java:483) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.access$100(LeaderSelector.java:66) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.leader.LeaderSelector$2.call(LeaderSelector.java:247) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at org.apache.curator.framework.recipes.leader.LeaderSelector$2.call(LeaderSelector.java:241) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-1      |   at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264) ~[na:na]
dbreader-app-1      |   at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:539) ~[na:na]
dbreader-app-1      |   at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264) ~[na:na]
dbreader-app-1      |   at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136) ~[na:na]
dbreader-app-1      |   at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635) ~[na:na]
dbreader-app-1      |   at java.base/java.lang.Thread.run(Thread.java:833) ~[na:na]
dbreader-app-1      |
dbreader-app-3      | 👑 [2025-09-24 00:58:15] Instance dbreader-app-3 became MASTER!
dbreader-app-1 exited with code 143
zookeeper           | [2025-09-24 00:58:20,321] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:51748, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-3      | 📊 [MASTER-dbreader-app-3] 2025-09-24 00:58:23 - No unprocessed records found
zookeeper           | [2025-09-24 00:58:30,392] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:42668, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:58:40,457] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:60804, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:58:50,529] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:43548, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:59:00,629] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:34182, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:59:10,675] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:53672, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:59:20,752] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:60156, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-3      | 📊 [MASTER-dbreader-app-3] 2025-09-24 00:59:23 - No unprocessed records found
zookeeper           | [2025-09-24 00:59:30,812] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:43228, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
zookeeper           | [2025-09-24 00:59:40,918] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:44034, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
```

**Yeni MASTER için yeni bir kayıt ekleyelim ve MASTER'ın işlediğini görelim:**

Belirli bir periyotla veri okuduğu için aradaki satırlarda göreceksiniz MASTER'ın (`dbreader-app-1`) kaydı okuduğunu.

```shell
zookeeper           | [2025-09-24 01:06:31,670] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:33824, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-3      | 📊 [MASTER-dbreader-app-3] 2025-09-24 01:06:33 - No unprocessed records found
zookeeper           | [2025-09-24 01:06:41,723] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:45676, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
...
dbreader-app-3      | 🔥 [MASTER-dbreader-app-3] 2025-09-24 01:07:33 - Processing 1 records:
dbreader-app-3      |    ✅ Processed record ID: 7 - Message: Test Wed Sep 24 01:06:33 UTC 2025
zookeeper           | [2025-09-24 01:07:42,115] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:39484, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
...
```

En son adımda aktif lideri kapatacağız (`dbreader-app-3`) ama önce MASTER olmaya aday bırakmayalım (`dbreader-app-2` kapansın) ve çıktıları görelim:

```shell
zookeeper           | [2025-09-24 01:10:13,161] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:52400, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-2      | 2025-09-24 01:10:14.644  INFO 1 --- [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
dbreader-app-2 exited with code 143
zookeeper           | [2025-09-24 01:10:23,239] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:38316, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)
dbreader-app-3      | 💔 [2025-09-24 01:10:26] Instance dbreader-app-3 lost leadership
dbreader-app-3      | 2025-09-24 01:10:26.453  INFO 1 --- [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
dbreader-app-3      | 2025-09-24 01:10:26.456 ERROR 1 --- [eaderSelector-0] o.a.c.f.recipes.leader.LeaderSelector    : The leader threw an exception
dbreader-app-3      |
dbreader-app-3      | java.lang.IllegalStateException: Expected state [STARTED] was [STOPPED]
dbreader-app-3      |   at org.apache.curator.shaded.com.google.common.base.Preconditions.checkState(Preconditions.java:821) ~[curator-client-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.imps.CuratorFrameworkImpl.checkState(CuratorFrameworkImpl.java:457) ~[curator-framework-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.imps.CuratorFrameworkImpl.delete(CuratorFrameworkImpl.java:477) ~[curator-framework-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.locks.LockInternals.deleteOurPath(LockInternals.java:347) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.locks.LockInternals.releaseLock(LockInternals.java:124) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.locks.InterProcessMutex.release(InterProcessMutex.java:154) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.doWork(LeaderSelector.java:454) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.doWorkLoop(LeaderSelector.java:483) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.leader.LeaderSelector.access$100(LeaderSelector.java:66) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.leader.LeaderSelector$2.call(LeaderSelector.java:247) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at org.apache.curator.framework.recipes.leader.LeaderSelector$2.call(LeaderSelector.java:241) ~[curator-recipes-5.4.0.jar!/:5.4.0]
dbreader-app-3      |   at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264) ~[na:na]
dbreader-app-3      |   at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:539) ~[na:na]
dbreader-app-3      |   at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264) ~[na:na]
dbreader-app-3      |   at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136) ~[na:na]
dbreader-app-3      |   at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635) ~[na:na]
dbreader-app-3      |   at java.base/java.lang.Thread.run(Thread.java:833) ~[na:na]
dbreader-app-3      |
dbreader-app-3 exited with code 143
zookeeper           | [2025-09-24 01:10:33,309] INFO Unable to read additional data from client, it probably closed the socket: address = /0:0:0:0:0:0:0:1:56304, session = 0x0 (org.apache.zookeeper.server.NIOServerCnxn)

```

## Zookeeper Nasıl Master Seçer?

Zookeeper ile Master Seçimi Nasıl Yapılır?
Zookeeper, dağıtık sistemlerde koordinasyon sağlamak için kullanılan güçlü bir servistir. Master seçimi, Zookeeper'ın en yaygın kullanım alanlarından biridir. Bu süreç genel olarak şu adımlarla işler:

1. Giriş (Join): Her uygulama örneği (burada her bir Docker konteyneri), Zookeeper'da belirli bir yolun altına (örneğin /master*election) geçici (ephemeral) ve sıralı (sequential) bir düğüm (znode) oluşturmaya çalışır. Zookeeper'da bu düğüm, ephemeral* ön eki ile gösterilebilir.

2. Sıra (Sequence): Zookeeper, her oluşturulan düğüme artan bir sıra numarası ekler (örneğin, /master_election/master_0000000001, /master_election/master_0000000002, vb.).

3. En Küçük Düğüm (Smallest Node): Uygulamalar, bu yol altındaki tüm düğümleri listeler ve kendi oluşturdukları düğümün en küçük numaralı düğüm olup olmadığını kontrol eder.

4. Master İlanı: Eğer bir uygulama örneğinin düğüm numarası, o yol altındaki en küçük numaralı düğüm ise, bu örnek kendisini master ilan eder.

5. Takipçiler (Followers): Eğer bir uygulamanın düğümü en küçük değilse, kendisinden bir önceki en küçük numaralı düğümü izlemeye başlar (watch). Zookeeper'ın ephemeral düğüm özelliği sayesinde, master olan uygulamanın bağlantısı koptuğunda (yani master çöktüğünde), bu düğüm otomatik olarak silinir.

6. Yeni Seçim (Re-election): Master'ın düğümü silindiğinde, Zookeeper bu durumu izleyen diğer uygulamalara bildirir. Bu bildirimle birlikte, bir sonraki en küçük numaralı düğümün sahibi olan uygulama, kendisini yeni master olarak ilan eder. Bu süreç, sistemde her zaman tek bir master olmasını garanti eder.

Bu mekanizma, master'ın çökmesi durumunda dahi hizmetin kesintisiz devam etmesini sağlar. Sistemde birden fazla master olmasını engellediği için tekil bir kontrol noktası (single point of control) oluşturulur.

### Java Uygulaması

Basit bir Spring Boot projesi kullanacağız. Maven bağımlılıkları şunlardır:

- `spring-boot-starter`: Spring Boot uygulamasının temel bağımlılıkları ve alt yapısı. (Basit bir web uygulaması da dahil)
- `spring-boot-starter-data-jpa`: Veritabanı işlemleri için JPA (Java Persistence API) desteği sağlar.
- `mysql-connector-j`: MySQL veritabanı sürücüsü, uygulamanın MySQL ile iletişim kurmasını sağlar.
- `org.apache.curator:curator-framework`: Apache ZooKeeper ile etkileşime geçmek için kullanılan yüksek seviyeli API.
- `org.apache.curator:curator-recipes`: Apache Curator tarafından sağlanan ek ZooKeeper işlemleri ve kolaylıklar.
- `com.h2database:h2`: Test ortamında kullanılan hafif, bellek içi veritabanı.
- `spring-boot-starter-test`: Spring Boot tabanlı uygulamalar için test kütüphaneleri.
- `org.testcontainers:junit-jupiter ve org.testcontainers:mysql`: Test konteynerleriyle (özellikle MySQL) entegrasyon ve test desteği.

## CLI Komutları

#### Temel Komutlar

```sh
mvn clean                    # Temizle
mvn compile                  # Derle
mvn test                     # Test çalıştır
mvn package                  # JAR oluştur
mvn clean package            # Temizle + derle + test + package
mvn clean install            # Local repository'e install et
```

#### Yazılım Geliştirme Komutları

```sh
# Uygulamayı çalıştır
mvn spring-boot:run

# "dev" profil ile çalıştır
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

#### Analiz ve Raporlama

```sh
mvn dependency:tree                      # Dependency ağacını göster
mvn dependency:analyze                   # Kullanılmayan dependency'leri bul
mvn versions:display-dependency-updates  # Güncellenebilir dependency'ler
mvn help:effective-pom                   # Effective POM'u göster
```

#### Test

```sh
mvn test                        # Unit testler
mvn integration-test            # Integration testler
mvn verify                      # Tüm testler
mvn test -Dtest=ClassName       # Belirli test class'ı
mvn test -DfailIfNoTests=false  # Test yoksa fail olma
```

#### Paketleme ve Dağıtım

```sh
mvn package -DskipTests                  # Test'leri atla
mvn package -Dmaven.test.skip=true       # Test compile'ını da atla
mvn clean package spring-boot:repackage  # Fat JAR oluştur
# Manual JAR install
mvn install:install-file -Dfile=myjar.jar -DgroupId=com.example -DartifactId=my-jar -Dversion=1.0 -Dpackaging=jar
```

```sh

```

```

```
