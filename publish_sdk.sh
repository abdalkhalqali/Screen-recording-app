#!/bin/bash

# QA Snap SDK Publishing Script
# This script helps automate the publishing process

set -e  # Exit on any error

echo "🚀 QA Snap SDK Publishing Script"
echo "================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_step() {
    echo -e "${BLUE}📋 $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if git is available
if ! command -v git &> /dev/null; then
    print_error "Git is not installed or not in PATH"
    exit 1
fi

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    print_error "Not in a git repository"
    exit 1
fi

# Get current version from gradle.properties
CURRENT_VERSION=$(grep "VERSION_NAME=" gradle.properties | cut -d'=' -f2)
print_step "Current version: $CURRENT_VERSION"

# Check for uncommitted changes
if [[ -n $(git status --porcelain) ]]; then
    print_warning "You have uncommitted changes. Commit them before publishing."
    git status --short
    
    read -p "Do you want to continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_error "Publishing cancelled"
        exit 1
    fi
fi

# Menu for publishing options
echo
print_step "Choose publishing option:"
echo "1) Local Maven (for testing)"
echo "2) JitPack (GitHub release)"
echo "3) Maven Central (requires setup)"
echo "4) Build only (no publishing)"
echo "5) Clean & Build"

read -p "Enter your choice (1-5): " choice

case $choice in
    1)
        print_step "Publishing to Local Maven..."
        ./gradlew qa-snap-sdk:publishToMavenLocal
        print_success "Published to local Maven repository"
        echo "You can now test with:"
        echo "  repositories { mavenLocal() }"
        echo "  implementation 'io.codingskuy:qa-snap-sdk:$CURRENT_VERSION'"
        ;;
    2)
        print_step "Preparing for JitPack publishing..."
        
        # Ask for version tag
        read -p "Enter version tag (current: $CURRENT_VERSION): " VERSION_TAG
        if [[ -z "$VERSION_TAG" ]]; then
            VERSION_TAG="v$CURRENT_VERSION"
        fi
        
        # Ensure tag starts with 'v'
        if [[ ! $VERSION_TAG =~ ^v ]]; then
            VERSION_TAG="v$VERSION_TAG"
        fi
        
        # Build first
        print_step "Building SDK..."
        ./gradlew qa-snap-sdk:clean qa-snap-sdk:assembleRelease
        
        # Check if tag already exists
        if git tag -l | grep -q "^$VERSION_TAG$"; then
            print_warning "Tag $VERSION_TAG already exists"
            read -p "Do you want to delete and recreate it? (y/N): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                git tag -d "$VERSION_TAG"
                git push origin --delete "$VERSION_TAG" 2>/dev/null || true
            else
                print_error "Publishing cancelled"
                exit 1
            fi
        fi
        
        # Create and push tag
        print_step "Creating git tag: $VERSION_TAG"
        git tag -a "$VERSION_TAG" -m "Release $VERSION_TAG"
        
        print_step "Pushing to GitHub..."
        git push origin main  # or master, depending on your default branch
        git push origin "$VERSION_TAG"
        
        print_success "Published to JitPack!"
        echo
        echo "📖 Usage instructions:"
        echo "repositories {"
        echo "    maven { url 'https://jitpack.io' }"
        echo "}"
        echo "dependencies {"
        echo "    implementation 'com.github.Coding-Skuy:qa-snap-sdk:$VERSION_TAG'"
        echo "}"
        echo
        echo "🔗 Check build status: https://jitpack.io/#Coding-Skuy/qa-snap-sdk"
        ;;
    3)
        print_step "Publishing to Maven Central..."
        print_warning "Make sure you have configured:"
        echo "  - Sonatype account"
        echo "  - GPG signing keys"
        echo "  - Credentials in ~/.gradle/gradle.properties"
        
        read -p "Continue with Maven Central publishing? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            # Uncomment the Maven Central script
            sed -i '' 's|^// apply from.*publish-maven.gradle.*|apply from: "../scripts/publish-maven.gradle"|' qa-snap-sdk/build.gradle
            
            ./gradlew qa-snap-sdk:clean qa-snap-sdk:publishReleasePublicationToSonatypeRepository
            
            print_success "Published to Maven Central staging"
            echo "Next steps:"
            echo "1. Login to https://s01.oss.sonatype.org/"
            echo "2. Go to Staging Repositories"
            echo "3. Find and close your repository"
            echo "4. Release the repository"
            
            # Restore build.gradle
            sed -i '' 's|^apply from.*publish-maven.gradle.*|// apply from: "../scripts/publish-maven.gradle"|' qa-snap-sdk/build.gradle
        else
            print_error "Maven Central publishing cancelled"
        fi
        ;;
    4)
        print_step "Building SDK only..."
        ./gradlew qa-snap-sdk:clean qa-snap-sdk:assembleRelease qa-snap-sdk:publishToMavenLocal
        print_success "Build completed"
        echo "AAR location: qa-snap-sdk/build/outputs/aar/"
        ;;
    5)
        print_step "Clean & Build..."
        ./gradlew clean build
        print_success "Clean & Build completed"
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac

# Show build artifacts
if [[ $choice == "2" || $choice == "4" ]]; then
    echo
    print_step "Build artifacts:"
    find qa-snap-sdk/build/outputs -name "*.aar" -o -name "*.jar" | while read file; do
        size=$(du -h "$file" | cut -f1)
        echo "  📦 $(basename "$file") ($size)"
    done
fi

# Final recommendations
echo
print_step "Next steps:"
echo "✅ Test the SDK in a sample project"
echo "✅ Update documentation if needed"
echo "✅ Create release notes"
echo "✅ Notify team members"

print_success "Publishing process completed! 🎉"