@echo off
setlocal

set ACCOUNT=767397820327
set REGION=ap-southeast-3
set REGISTRY=%ACCOUNT%.dkr.ecr.%REGION%.amazonaws.com

echo === ECR Login ===
aws ecr get-login-password --region %REGION% | docker login --username AWS --password-stdin %REGISTRY%
if %errorlevel% neq 0 (echo ECR login failed & exit /b 1)

echo === Building JARs ===
call mvn clean package -DskipTests
if %errorlevel% neq 0 (echo Maven build failed & exit /b 1)

for %%s in (auth gateway order inventory payment orchestrator) do (
    echo === Building %%s ===
    docker build --no-cache -t %REGISTRY%/reactive-order/%%s:latest ./%%s-service
    if %errorlevel% neq 0 (echo Docker build failed for %%s & exit /b 1)

    echo === Pushing %%s ===
    docker push %REGISTRY%/reactive-order/%%s:latest
    if %errorlevel% neq 0 (echo Docker push failed for %%s & exit /b 1)
)

echo === All images pushed successfully ===
