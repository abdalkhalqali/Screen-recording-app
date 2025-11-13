#!/bin/bash

# QA Snap Debug Script - Multi-Environment Testing
# Script ini membantu debugging dan testing QA Snap integration

echo "🔍 QA Snap Debug Helper"
echo "======================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Function to check installed apps
check_installed_apps() {
    echo ""
    print_status "Checking installed QA Snap Demo apps..."
    
    # Check for each environment
    if adb shell pm list packages | grep -q "io.codingskuy.qa_snap_demo.dev"; then
        print_success "Development app installed: io.codingskuy.qa_snap_demo.dev"
    else
        print_warning "Development app not installed"
    fi
    
    if adb shell pm list packages | grep -q "io.codingskuy.qa_snap_demo.staging"; then
        print_success "Staging app installed: io.codingskuy.qa_snap_demo.staging"
    else
        print_warning "Staging app not installed"
    fi
    
    if adb shell pm list packages | grep -q "io.codingskuy.qa_snap_demo$"; then
        print_success "Production app installed: io.codingskuy.qa_snap_demo"
    else
        print_warning "Production app not installed"
    fi
}

# Function to check app permissions
check_permissions() {
    local package_name=$1
    echo ""
    print_status "Checking permissions for: $package_name"
    
    # Check key permissions
    local audio_perm=$(adb shell dumpsys package $package_name | grep "android.permission.RECORD_AUDIO" | grep "granted=true")
    local notif_perm=$(adb shell dumpsys package $package_name | grep "android.permission.POST_NOTIFICATIONS" | grep "granted=true")
    
    if [ ! -z "$audio_perm" ]; then
        print_success "RECORD_AUDIO: Granted"
    else
        print_error "RECORD_AUDIO: Not granted"
    fi
    
    if [ ! -z "$notif_perm" ]; then
        print_success "POST_NOTIFICATIONS: Granted"
    else
        print_error "POST_NOTIFICATIONS: Not granted"
    fi
}

# Function to check QA Snap files
check_qa_files() {
    local package_name=$1
    echo ""
    print_status "Checking QA Snap files for: $package_name"
    
    local app_data_dir="/Android/data/$package_name/files"
    
    # Check if directories exist
    if adb shell ls "$app_data_dir" 2>/dev/null | grep -q "QASnapRecordings"; then
        local video_count=$(adb shell ls "$app_data_dir/QASnapRecordings" 2>/dev/null | wc -l)
        print_success "QASnapRecordings directory found - $video_count files"
    else
        print_warning "QASnapRecordings directory not found"
    fi
    
    if adb shell ls "$app_data_dir" 2>/dev/null | grep -q "QASnapLogs"; then
        local log_count=$(adb shell ls "$app_data_dir/QASnapLogs" 2>/dev/null | wc -l)
        print_success "QASnapLogs directory found - $log_count files"
    else
        print_warning "QASnapLogs directory not found"
    fi
}

# Function to watch QA Snap logs
watch_qa_logs() {
    local package_name=${1:-"io.codingskuy.qa_snap_demo.staging"}
    echo ""
    print_status "Watching QA Snap logs for: $package_name"
    print_warning "Press Ctrl+C to stop watching"
    echo ""
    
    adb logcat -c  # Clear previous logs
    adb logcat | grep -E "(EnvironmentManager|QASnapHelper|QASnapRecorder|$package_name)"
}

# Function to grant permissions
grant_permissions() {
    local package_name=$1
    echo ""
    print_status "Granting permissions for: $package_name"
    
    adb shell pm grant $package_name android.permission.RECORD_AUDIO
    adb shell pm grant $package_name android.permission.POST_NOTIFICATIONS
    adb shell pm grant $package_name android.permission.WRITE_EXTERNAL_STORAGE
    
    print_success "Permissions granted"
}

# Function to start app
start_app() {
    local package_name=$1
    echo ""
    print_status "Starting app: $package_name"
    
    adb shell monkey -p $package_name -c android.intent.category.LAUNCHER 1
}

# Function to show environment status
show_environment_status() {
    echo ""
    echo "📊 Environment Status:"
    echo "====================="
    
    # Check each environment
    for env in "dev" "staging" "production"; do
        local package=""
        case $env in
            "dev") package="io.codingskuy.qa_snap_demo.dev" ;;
            "staging") package="io.codingskuy.qa_snap_demo.staging" ;;
            "production") package="io.codingskuy.qa_snap_demo" ;;
        esac
        
        printf "%-12s" "$env:"
        if adb shell pm list packages | grep -q "$package"; then
            printf " ✅ Installed"
            # Check if app is running
            if adb shell ps | grep -q "$package"; then
                printf " | 🟢 Running"
            else
                printf " | ⚫ Not running"
            fi
        else
            printf " ❌ Not installed"
        fi
        echo ""
    done
}

# Main script logic
case "$1" in
    "check"|"status")
        check_installed_apps
        show_environment_status
        ;;
    "permissions")
        if [ -z "$2" ]; then
            echo "Usage: $0 permissions <package_name>"
            echo "Example: $0 permissions io.codingskuy.qa_snap_demo.staging"
            exit 1
        fi
        check_permissions $2
        ;;
    "grant")
        if [ -z "$2" ]; then
            echo "Usage: $0 grant <package_name>"
            echo "Example: $0 grant io.codingskuy.qa_snap_demo.staging"
            exit 1
        fi
        grant_permissions $2
        ;;
    "files")
        if [ -z "$2" ]; then
            echo "Usage: $0 files <package_name>"
            echo "Example: $0 files io.codingskuy.qa_snap_demo.staging"
            exit 1
        fi
        check_qa_files $2
        ;;
    "logs"|"watch")
        watch_qa_logs $2
        ;;
    "start")
        if [ -z "$2" ]; then
            echo "Usage: $0 start <package_name>"
            echo "Example: $0 start io.codingskuy.qa_snap_demo.staging"
            exit 1
        fi
        start_app $2
        ;;
    "test-staging")
        print_status "Testing staging environment..."
        check_installed_apps
        check_permissions "io.codingskuy.qa_snap_demo.staging"
        check_qa_files "io.codingskuy.qa_snap_demo.staging" 
        print_status "Starting staging app..."
        start_app "io.codingskuy.qa_snap_demo.staging"
        ;;
    "full-debug")
        echo ""
        print_status "Full debug check for all environments..."
        check_installed_apps
        show_environment_status
        
        for package in "io.codingskuy.qa_snap_demo.dev" "io.codingskuy.qa_snap_demo.staging" "io.codingskuy.qa_snap_demo"; do
            if adb shell pm list packages | grep -q "$package"; then
                check_permissions $package
                check_qa_files $package
            fi
        done
        ;;
    *)
        echo "Usage: $0 {check|permissions|grant|files|logs|start|test-staging|full-debug}"
        echo ""
        echo "Commands:"
        echo "  check                    - Check installed apps and status"
        echo "  permissions <package>    - Check app permissions"
        echo "  grant <package>          - Grant required permissions"
        echo "  files <package>          - Check QA Snap output files"
        echo "  logs [package]           - Watch QA Snap logs (default: staging)"
        echo "  start <package>          - Start specific app"
        echo "  test-staging             - Quick test staging environment"
        echo "  full-debug               - Full debug check for all environments"
        echo ""
        echo "Package Names:"
        echo "  Development: io.codingskuy.qa_snap_demo.dev"
        echo "  Staging:     io.codingskuy.qa_snap_demo.staging"
        echo "  Production:  io.codingskuy.qa_snap_demo"
        echo ""
        echo "Examples:"
        echo "  $0 check                 - Check all environments"
        echo "  $0 grant io.codingskuy.qa_snap_demo.staging"
        echo "  $0 logs                  - Watch staging logs"
        echo "  $0 test-staging          - Quick staging test"
        exit 1
        ;;
esac

echo ""
print_status "🔍 Debug operations completed!"