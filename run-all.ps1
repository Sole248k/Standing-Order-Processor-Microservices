# EastWest Bank Standing Order Processor
# Launches all 5 microservices locally in background processes

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting EWB Standing Order Processor Microservices Suite " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "Payment Service"; Dir = "ewb-payment-service"; Port = 8083; Jar = "ewb-payment-service/target/ewb-payment-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "Standing Order Service"; Dir = "ewb-standing-order-service"; Port = 8081; Jar = "ewb-standing-order-service/target/ewb-standing-order-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "Notification Service"; Dir = "ewb-notification-service"; Port = 8084; Jar = "ewb-notification-service/target/ewb-notification-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "Execution Service"; Dir = "ewb-execution-service"; Port = 8082; Jar = "ewb-execution-service/target/ewb-execution-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "API Gateway & Frontend"; Dir = "ewb-gateway"; Port = 8080; Jar = "ewb-gateway/target/ewb-gateway-1.0.0-SNAPSHOT.jar" }
)

$processes = @()

foreach ($svc in $services) {
    Write-Host "Launching $($svc.Name) on port $($svc.Port)..." -ForegroundColor Yellow
    $p = Start-Process -FilePath "java" -ArgumentList "-jar", $svc.Jar -PassThru -NoNewWindow
    $processes += $p
    Start-Sleep -Seconds 4
}

Write-Host "`nAll microservices launched successfully!" -ForegroundColor Green
Write-Host "Access the Web Dashboard at: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Press Ctrl+C or close this terminal window to stop all services." -ForegroundColor Gray

# Wait on processes
try {
    $processes | Wait-Process
} finally {
    Write-Host "Stopping all microservices..." -ForegroundColor Red
    $processes | Stop-Process -Force -ErrorAction SilentlyContinue
}
