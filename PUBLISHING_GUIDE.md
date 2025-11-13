# QA Snap SDK Publishing Guide

Panduan lengkap untuk mempublikasikan QA Snap SDK ke Maven repositories.

## Opsi Publishing

Ada 3 pilihan utama untuk mempublikasikan SDK:

### 1. JitPack (Recommended untuk Pemula) 🚀

**Keuntungan:**

- Gratis dan mudah setup
- Tidak perlu akun khusus
- Otomatis build dari GitHub
- Mendukung versioning dengan Git tags

**Langkah-langkah:**

1. **Push kode ke GitHub repository**
2. **Buat release tag:**
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

3. **Pengguna dapat menggunakan dependency:**
   ```gradle
   // Di settings.gradle atau build.gradle (project level)
   repositories {
       maven { url 'https://jitpack.io' }
   }
   
   // Di build.gradle (app level)
   dependencies {
       implementation 'com.github.yourusername:your-repo-name:v1.0.0'
   }
   ```

4. **Check build status di:** `https://jitpack.io/#yourusername/your-repo-name`

### 2. Maven Central (Recommended untuk Production) 🏆

**Keuntungan:**

- Repository resmi dan terpercaya
- Integrasi sempurna dengan semua build tools
- Digunakan oleh library besar seperti Google, Square, dll

**Setup Requirements:**

1. **Buat akun Sonatype JIRA:**
    - Daftar di: https://issues.sonatype.org/secure/Signup!default.jspa
    - Buat ticket untuk group ID
      baru: https://issues.sonatype.org/secure/CreateIssue.jspa?issuetype=21&pid=10134

2. **Generate GPG key untuk signing:**
   ```bash
   # Generate key
   gpg --gen-key
   
   # List keys
   gpg --list-keys
   
   # Export public key ke keyserver
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   
   # Export private key
   gpg --export-secret-keys YOUR_KEY_ID > secring.gpg
   ```

3. **Setup credentials di `~/.gradle/gradle.properties`:**
   ```properties
   SONATYPE_USERNAME=your_sonatype_username
   SONATYPE_PASSWORD=your_sonatype_password
   SIGNING_KEY_ID=your_gpg_key_id
   SIGNING_PASSWORD=your_gpg_key_password
   SIGNING_SECRET_KEY_RING_FILE=/path/to/secring.gpg
   ```

4. **Enable Maven Central publishing:**
   ```gradle
   // Di qa-snap-sdk/build.gradle, uncomment baris ini:
   apply from: '../scripts/publish-maven.gradle'
   ```

5. **Publish ke staging:**
   ```bash
   ./gradlew qa-snap-sdk:publishReleasePublicationToSonatypeRepository
   ```

6. **Promote di Sonatype Nexus:**
    - Login ke: https://s01.oss.sonatype.org/
    - Go to Staging Repositories
    - Find dan close repository
    - Release repository

### 3. GitHub Packages 📦

**Keuntungan:**

- Terintegrasi dengan GitHub
- Kontrol akses yang baik
- Gratis untuk public repositories

**Setup:**

1. **Generate GitHub Personal Access Token:**
    - Settings → Developer settings → Personal access tokens
    - Enable `write:packages` scope

2. **Setup di build.gradle:**
   ```gradle
   publishing {
       repositories {
           maven {
               name = "GitHubPackages"
               url = "https://maven.pkg.github.com/yourusername/your-repo-name"
               credentials {
                   username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                   password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
               }
           }
       }
   }
   ```

3. **Publish:**
   ```bash
   ./gradlew qa-snap-sdk:publish -Pgpr.user=yourusername -Pgpr.key=your_token
   ```

## Build Commands

### Untuk JitPack

```bash
# Build AAR untuk JitPack
./gradlew qa-snap-sdk:assembleRelease

# Check publishing setup
./gradlew qa-snap-sdk:publishToMavenLocal
```

### Untuk Maven Central

```bash
# Build semua artifacts
./gradlew qa-snap-sdk:assembleRelease
./gradlew qa-snap-sdk:androidSourcesJar
./gradlew qa-snap-sdk:androidJavadocsJar

# Publish ke local untuk testing
./gradlew qa-snap-sdk:publishToMavenLocal

# Publish ke staging
./gradlew qa-snap-sdk:publishReleasePublicationToSonatypeRepository
```

## Versioning Strategy

Gunakan [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes (1.0.0 → 2.0.0)
- **MINOR**: New features, backward compatible (1.0.0 → 1.1.0)
- **PATCH**: Bug fixes (1.0.0 → 1.0.1)

Update version di:

1. `gradle.properties` → `VERSION_NAME`
2. `qa-snap-sdk/build.gradle` → `versionName`
3. Git tag untuk release

## Testing Integration

Sebelum publish, test integration di project lain:

```gradle
// Test dari local Maven
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.codingskuy:qa-snap-sdk:1.0.0'
}
```

## Checklist Before Publishing

- [ ] All tests passing
- [ ] Code coverage acceptable
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version number incremented
- [ ] ProGuard rules complete
- [ ] Permissions documented
- [ ] Example app works
- [ ] README.md updated with new version

## Integration Examples

### Basic Integration

```gradle
// settings.gradle
repositories {
    maven { url 'https://jitpack.io' } // untuk JitPack
    // atau mavenCentral() untuk Maven Central
}

// app/build.gradle
dependencies {
    implementation 'io.codingskuy:qa-snap-sdk:1.0.0'
}
```

### Advanced Integration

```kotlin
class MainActivity : AppCompatActivity(), QASnapCallback {
    private lateinit var qaSnapHelper: QASnapHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize QA Snap
        qaSnapHelper = QASnapHelper(this)
        qaSnapHelper.initialize()
    }
    
    override fun onQARecordingReady() {
        // SDK ready
    }
    
    override fun shouldAutoStartRecording(): Boolean = false
    
    // ... implement other callbacks
}
```

## Troubleshooting

### Common Issues:

1. **Build fails with "Task assembleRelease not found"**
   ```bash
   ./gradlew qa-snap-sdk:tasks --all
   ```

2. **GPG signing fails**
   ```bash
   # Check key exists
   gpg --list-secret-keys
   
   # Verify signing setup
   ./gradlew qa-snap-sdk:signReleasePublication
   ```

3. **JitPack build fails**
    - Check `jitpack.yml` configuration
    - Verify JDK version compatibility
    - Check build logs at jitpack.io

4. **Dependencies not resolved**
    - Ensure all repositories are added
    - Check version compatibility
    - Verify group ID and artifact ID

## CI/CD Integration

### GitHub Actions Example:

```yaml
name: Publish to Maven Central

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'adopt'
    
    - name: Publish to Maven Central
      run: ./gradlew qa-snap-sdk:publishReleasePublicationToSonatypeRepository
      env:
        OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
        OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
        SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
        SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
        SIGNING_SECRET_KEY_RING_FILE: ${{ secrets.SIGNING_SECRET_KEY_RING_FILE }}
```

## Next Steps

1. **Choose your publishing method** (JitPack recommended untuk start)
2. **Update URL dan credentials** di `gradle.properties`
3. **Test build** dengan `./gradlew qa-snap-sdk:assembleRelease`
4. **Create GitHub repository** dan push code
5. **Follow publishing steps** sesuai metode yang dipilih

Happy Publishing! 🚀