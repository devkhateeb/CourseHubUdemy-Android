@echo off
setlocal
title Publish CourseHubUdemy Android Project to GitHub
echo ================================================
echo CourseHubUdemy - GitHub Upload Helper
echo ================================================
echo.
where git >nul 2>nul
if errorlevel 1 (
  echo Git was not found.
  echo Install Git for Windows from https://git-scm.com/download/win
  echo Then run this file again from the extracted project folder.
  pause
  exit /b 1
)
echo First, create an EMPTY repository at https://github.com/new
echo Do not add a README, license, or .gitignore on GitHub.
echo.
set /p REPO_URL=Paste your GitHub repository URL (example: https://github.com/USERNAME/CourseHubUdemy-Android.git) and press Enter: 
if "%REPO_URL%"=="" (
  echo No repository URL entered.
  pause
  exit /b 1
)
if not exist ".github\workflows\build-apk.yml" (
  echo ERROR: Workflow file .github\workflows\build-apk.yml is missing.
  echo Run this helper from the extracted project root.
  pause
  exit /b 1
)
if not exist "settings.gradle" (
  echo ERROR: settings.gradle is missing.
  echo Run this helper from the extracted project root.
  pause
  exit /b 1
)
git init
if errorlevel 1 goto failed
git branch -M main
git add .
if errorlevel 1 goto failed
git commit -m "Upload CourseHubUdemy Android project"
if errorlevel 1 (
  echo.
  echo Commit may have failed because Git identity is not configured.
  echo Configure it with: git config --global user.name "Your Name"
  echo and: git config --global user.email "you@example.com"
  goto failed
)
git remote remove origin >nul 2>nul
git remote add origin "%REPO_URL%"
if errorlevel 1 goto failed
git push -u origin main
if errorlevel 1 (
  echo.
  echo Push failed. Check the repository URL and sign-in/authentication.
  goto failed
)
echo.
echo SUCCESS! Your project is now uploaded to GitHub.
echo Open the repository Actions tab to run Build CourseHub Udemy APK.
pause
exit /b 0
:failed
echo.
echo The upload did not finish. Read the error above and try again.
pause
exit /b 1
