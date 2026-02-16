# Road Rats Backend Setup Guide

## Prerequisites
- Java 17 or higher
- MS SQL Server database access
- Gradle (included via wrapper)

## Database Configuration

### Option 1: Environment Variables (Recommended)

Set these environment variables before running the application:

**Windows PowerShell:**
```powershell
$env:DB_URL="jdbc:sqlserver://your-server:1433;databaseName=WMSSQL-CLS;encrypt=true;trustServerCertificate=true"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
```

**Windows CMD:**
```cmd
set DB_URL=jdbc:sqlserver://your-server:1433;databaseName=WMSSQL-CLS;encrypt=true;trustServerCertificate=true
set DB_USERNAME=your_username
set DB_PASSWORD=your_password
```

**Linux/Mac:**
```bash
export DB_URL="jdbc:sqlserver://your-server:1433;databaseName=WMSSQL-CLS;encrypt=true;trustServerCertificate=true"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_password"
```

### Option 2: application-local.properties

Create `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:sqlserver://your-server:1433;databaseName=WMSSQL-CLS;encrypt=true;trustServerCertificate=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```

Then run with: `./gradlew bootRun --args='--spring.profiles.active=local'`

## Database Table Structure

The application expects a table named `CLS_QUEUE` with the following structure:

```sql
CREATE TABLE CLS_QUEUE (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    ROUTE_ID NVARCHAR(255) NOT NULL,
    STATUS NVARCHAR(50) NOT NULL,
    PRIORITY INT,
    CREATED_AT DATETIME2,
    UPDATED_AT DATETIME2,
    DATA NVARCHAR(MAX)
);
```

## Running the Application

1. **Build the project:**
   ```bash
   ./gradlew build
   ```

2. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

   Or on Windows:
   ```cmd
   gradlew.bat bootRun
   ```

3. **The API will be available at:**
   - Base URL: `http://localhost:8080`
   - Health Check: `http://localhost:8080/api/health`
   - CLS Queue API: `http://localhost:8080/api/cls/queue`

## API Endpoints

### Health Check
- `GET /api/health` - Check if backend is running

### CLS Queue Management
- `GET /api/cls/queue` - Get all queue items
- `GET /api/cls/queue/statistics` - Get queue statistics
- `GET /api/cls/queue/{id}` - Get queue item by ID
- `GET /api/cls/queue/status/{status}` - Get queue items by status
- `POST /api/cls/queue` - Create new queue item
- `PUT /api/cls/queue/{id}` - Update queue item
- `DELETE /api/cls/queue/{id}` - Delete queue item
- `GET /api/cls/database/test` - Test database connection

## Testing the Connection

1. Start the application
2. Visit: `http://localhost:8080/api/health`
3. You should see: `{"status":"UP","message":"Road Rats Backend is running"}`

## Troubleshooting

### Database Connection Issues
- Verify your SQL Server is running and accessible
- Check firewall settings
- Ensure the database name exists
- Verify username and password are correct
- Try using `trustServerCertificate=true` in the connection string

### Port Already in Use
- Change the port in `application.properties`: `server.port=8081`
- Or stop the process using port 8080

