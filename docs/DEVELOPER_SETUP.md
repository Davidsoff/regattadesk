# Developer Setup Guide

This guide will help you set up the RegattaDesk development environment for the first time.

## Overview

RegattaDesk is a full-stack application with:
- **Backend**: Quarkus (Java 21) REST API
- **Frontend**: Vue 3 + Vite single-page application
- **Infrastructure**: Docker Compose stack (future: PostgreSQL, Traefik, Authelia, MinIO)

## Prerequisites Installation

### 1. Install Java 21

#### macOS (using Homebrew)
```bash
brew install openjdk@21
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

#### Windows
Download and install from [Adoptium](https://adoptium.net/temurin/releases/?version=21).

Verify installation:
```bash
java -version
# Should output: openjdk version "21.x.x"
```

### 2. Install Node.js 22+

#### macOS (using Homebrew)
```bash
brew install node@22
```

#### Linux (using NodeSource)
```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
```

#### Windows
Download and install from [nodejs.org](https://nodejs.org/).

Verify installation:
```bash
node --version  # Should be v22.x.x or higher
npm --version
```

### 3. Install Docker Desktop

Download and install Docker Desktop for your platform:
- **macOS**: [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
- **Windows**: [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
- **Linux**: Follow the [official Docker installation guide](https://docs.docker.com/engine/install/)

Verify installation:
```bash
docker --version
docker compose version  # Should be 2.24 or higher
```

### 4. Install Make (Optional but Recommended)

#### macOS
Make is included with Xcode Command Line Tools:
```bash
xcode-select --install
```

#### Linux
```bash
sudo apt install make  # Ubuntu/Debian
sudo yum install make  # CentOS/RHEL
```

#### Windows
Install via [Chocolatey](https://chocolatey.org/):
```bash
choco install make
```

Or use WSL2 (Windows Subsystem for Linux) for a better experience.

## First-Time Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Davidsoff/regattadesk.git
cd regattadesk
```

### 2. Install All Dependencies

Using Make:
```bash
make install
```

Or manually:
```bash
# Backend dependencies
cd apps/backend
./mvnw clean install -DskipTests
cd ../..

# Frontend dependencies
cd apps/frontend
npm install
cd ../..
```

### 3. Verify the Setup

Build both applications:
```bash
make build
```

If successful, you should see:
- Backend JAR file in `apps/backend/target/`
- Frontend build in `apps/frontend/dist/`

## Running the Application

### Development Mode

For local development, run both applications in separate terminal windows:

**Terminal 1 - Backend:**
```bash
make backend-dev
# Backend will be available at http://localhost:8080
# Live reload is enabled - changes to Java files will trigger automatic recompilation
```

**Terminal 2 - Frontend:**
```bash
make frontend-dev
# Frontend will be available at http://localhost:5173
# Hot module replacement (HMR) is enabled - changes are reflected immediately
```

### Verify Applications are Running

1. **Backend Health Check:**
   - Open `http://localhost:8080/api/health`
   - Should return: `{"status":"UP","version":"0.1.0-SNAPSHOT"}`

2. **Quarkus Health Check:**
   - Open `http://localhost:8080/q/health`
   - Should return overall health status

3. **Frontend:**
   - Open `http://localhost:5173`
   - Should display the Vue application

## Development Workflow

### Making Changes

1. **Backend Changes** (Java):
   - Edit files in `apps/backend/src/`
   - Quarkus dev mode will automatically recompile
   - Refresh your browser or API client to see changes

2. **Frontend Changes** (Vue/JavaScript):
   - Edit files in `apps/frontend/src/`
   - Vite HMR will update the browser automatically
   - No refresh needed in most cases

### Running Tests

Backend tests:
```bash
cd apps/backend
./mvnw test
```

### Building for Production

```bash
make build
```

Or individually:
```bash
# Backend
cd apps/backend
./mvnw clean package

# Frontend
cd apps/frontend
npm run build
```

## IDE Setup

### Visual Studio Code (Recommended)

Install the following extensions:
- **Java**: Extension Pack for Java (Microsoft)
- **Quarkus**: Quarkus (Red Hat)
- **Vue**: Vue - Official (Vue)
- **JavaScript/TypeScript**: ESLint, Prettier

### IntelliJ IDEA

IntelliJ IDEA Ultimate has built-in support for:
- Java/Maven projects
- Quarkus
- Vue.js

Open the `apps/backend` or `apps/frontend` folder directly.

### Eclipse

Import as existing Maven project:
1. File → Import → Maven → Existing Maven Projects
2. Select `apps/backend` directory

## Common Issues

### Java Version Mismatch

**Error**: `Unsupported class file major version`

**Solution**: Ensure you're using Java 21:
```bash
java -version
# If wrong version, set JAVA_HOME to Java 21
export JAVA_HOME=/path/to/java-21
```

### Node Version Mismatch

**Error**: `The engine "node" is incompatible`

**Solution**: Upgrade to Node.js 22+:
```bash
node --version
# Use nvm to manage Node versions if needed
```

### Port Already in Use

**Error**: `Port 8080 is already in use`

**Solution**: 
- Kill the process using the port, or
- Configure a different port in `apps/backend/src/main/resources/application.properties`:
  ```properties
  quarkus.http.port=8081
  ```

### Maven Wrapper Permission Error

**Error**: `Permission denied: ./mvnw`

**Solution**:
```bash
chmod +x apps/backend/mvnw
```

## Directory Structure

```
regattadesk/
├── apps/
│   ├── backend/          # Quarkus backend
│   │   ├── src/
│   │   │   ├── main/java/com/regattadesk/
│   │   │   └── test/
│   │   ├── pom.xml       # Maven configuration
│   │   └── mvnw          # Maven wrapper
│   │
│   └── frontend/         # Vue frontend
│       ├── src/
│       │   ├── components/
│       │   ├── App.vue
│       │   └── main.js
│       ├── package.json  # NPM configuration
│       └── vite.config.js
│
├── docs/                 # Documentation
├── infra/compose/        # Docker Compose (future)
├── tests/                # Integration tests (future)
├── Makefile              # Build commands
└── README.md             # Main documentation
```

## Next Steps

1. ✅ Complete the setup above
2. ✅ Run both backend and frontend in dev mode
3. ✅ Verify health endpoints
4. 📖 Read the [main README](../README.md) for common commands
5. 📖 Review [BC01 Platform Spec](../pdd/implementation/bc01-platform-and-delivery.md)
6. 🚀 Start development!

## Getting Help

- Check the main [README](../README.md)
- Review the [Implementation Plan](../pdd/implementation/plan.md)
- Search existing GitHub issues
- Create a new issue if you encounter problems

## Environment Variables

Currently, no environment variables are required for local development. Future iterations will add:
- Database connection strings
- Authentication configuration
- Object storage credentials

These will be documented when implemented.
