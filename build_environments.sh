#!/bin/bash

# Multi-Environment Build Script untuk QA Snap Demo (Product Flavors)
# Script ini memudahkan build dan testing berbagai environment menggunakan Product Flavors

echo "🏗️ QA Snap Demo - Multi Environment Builder (Product Flavors)"
echo "=============================================================="

# Colors for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to capitalize first letter
capitalize() {
    local word=$1
    echo "$(echo ${word:0:1} | tr '[:lower:]' '[:upper:]')${word:1}"
}

# Function to build specific environment flavor
build_environment() {
    local flavor=$1
    local build_type=$2
    local description=$3
    local variant="$(capitalize $flavor)$(capitalize $build_type)"  # e.g., DevelopmentDebug, StagingRelease
    
    echo ""
    print_status "Building $description..."
    print_status "Flavor: $flavor"
    print_status "Build Type: $build_type"
    print_status "Variant: $variant"
    
    if ./gradlew assemble${variant}; then
        print_success "$description build completed!"
        
        # Find and display APK info
        local apk_path="qa-snap-demo/build/outputs/apk/${flavor}/${build_type}"
        if [ -d "$apk_path" ]; then
            local apk_file=$(find "$apk_path" -name "*.apk" | head -1)
            if [ -f "$apk_file" ]; then
                local size=$(du -h "$apk_file" | cut -f1)
                print_success "APK Location: $apk_file"
                print_success "APK Size: $size"
            fi
        fi
    else
        print_error "$description build failed!"
        return 1
    fi
}

# Function to show environment comparison
show_environment_comparison() {
    echo ""
    echo "📊 Environment Comparison (Product Flavors):"
    echo "============================================="
    echo ""
    printf "%-15s %-25s %-15s %-15s %-20s\n" "Flavor" "Application ID" "QA Snap" "Logging" "App Name"
    printf "%-15s %-25s %-15s %-15s %-20s\n" "------" "-------------" "--------" "-------" "--------"
    printf "%-15s %-25s %-15s %-15s %-20s\n" "development" "...demo.dev" "❌ DISABLED" "✅ ENABLED" "Demo (Dev)"
    printf "%-15s %-25s %-15s %-15s %-20s\n" "staging" "...demo.staging" "✅ ENABLED" "✅ ENABLED" "Demo (Staging)"
    printf "%-15s %-25s %-15s %-15s %-20s\n" "production" "...demo" "❌ DISABLED" "❌ DISABLED" "Demo"
    echo ""
    echo "Available Build Variants:"
    echo "- developmentDebug / developmentRelease"
    echo "- stagingDebug / stagingRelease (QA Snap enabled)"
    echo "- productionDebug / productionRelease"
    echo ""
}

# Function to show build commands
show_build_commands() {
    echo "🔧 Available Build Commands (Product Flavors):"
    echo "==============================================="
    echo "Development Flavor:"
    echo "  Debug:   ./gradlew assembleDevelopmentDebug"
    echo "  Release: ./gradlew assembleDevelopmentRelease"
    echo ""
    echo "Staging Flavor (QA Snap enabled):"
    echo "  Debug:   ./gradlew assembleStagingDebug"
    echo "  Release: ./gradlew assembleStagingRelease"
    echo ""
    echo "Production Flavor:"
    echo "  Debug:   ./gradlew assembleProductionDebug"
    echo "  Release: ./gradlew assembleProductionRelease"
    echo ""
}

# Function to show testing instructions
show_testing_instructions() {
    echo "🧪 Testing Instructions (Product Flavors):"
    echo "==========================================="
    echo ""
    echo "Development Flavor:"
    echo "- Build: ./gradlew assembleDevelopmentDebug"
    echo "- QA Snap will NOT appear"
    echo "- Toast: 'Development environment - QA Snap disabled'"
    echo ""
    echo "Staging Flavor:"
    echo "- Build: ./gradlew assembleStagingDebug"
    echo "- QA Snap will automatically activate"
    echo "- MediaProjection permission dialog appears"
    echo "- Toast: 'Staging Environment - QA Recording Active'"
    echo ""
    echo "Production Flavor:"
    echo "- Build: ./gradlew assembleProductionRelease"
    echo "- QA Snap will NOT appear"
    echo "- No debug toasts"
    echo "- Clean production experience"
    echo ""
}

# Main script logic
case "$1" in
    "dev"|"development")
        build_type=${2:-"debug"}
        build_environment "development" "$build_type" "Development Environment ($build_type)"
        ;;
    "staging")
        build_type=${2:-"debug"}
        build_environment "staging" "$build_type" "Staging Environment ($build_type) - QA Snap Enabled"
        ;;
    "prod"|"production")
        build_type=${2:-"release"}
        build_environment "production" "$build_type" "Production Environment ($build_type)"
        ;;
    "all")
        print_status "Building all environment flavors..."
        build_environment "development" "debug" "Development Debug"
        build_environment "development" "release" "Development Release"
        build_environment "staging" "debug" "Staging Debug (QA Snap Enabled)"
        build_environment "staging" "release" "Staging Release (QA Snap Enabled)"
        build_environment "production" "debug" "Production Debug"
        build_environment "production" "release" "Production Release"
        print_success "All environment flavors built successfully!"
        ;;
    "clean")
        print_status "Cleaning project..."
        ./gradlew clean
        print_success "Project cleaned!"
        ;;
    "info"|"compare")
        show_environment_comparison
        ;;
    "help"|"commands")
        show_build_commands
        ;;
    "test"|"testing")
        show_testing_instructions
        ;;
    *)
        echo "Usage: $0 {dev|staging|prod|all|clean|info|help|test} [debug|release]"
        echo ""
        echo "Commands:"
        echo "  dev [debug|release]   - Build development flavor (QA Snap disabled)"
        echo "  staging [debug|release] - Build staging flavor (QA Snap ENABLED)"
        echo "  prod [debug|release]  - Build production flavor (QA Snap disabled)"
        echo "  all                   - Build all flavor combinations"
        echo "  clean                 - Clean project"
        echo "  info                  - Show environment comparison"
        echo "  help                  - Show build commands"
        echo "  test                  - Show testing instructions"
        echo ""
        echo "Examples:"
        echo "  $0 dev debug         - Build developmentDebug"
        echo "  $0 staging release   - Build stagingRelease (QA Snap enabled)"
        echo "  $0 prod release      - Build productionRelease"
        echo ""
        show_environment_comparison
        exit 1
        ;;
esac

echo ""
print_status "✨ Multi-environment build system ready!"
print_warning "Remember: QA Snap is ONLY active in staging environment for security!"
echo ""