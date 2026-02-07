# Health Endpoint Example

## Request

```bash
curl http://localhost:8080/health
```

## Response

```json
{
  "status": "healthy",
  "uptime": {
    "milliseconds": 123456789,
    "seconds": 123456,
    "formatted": "34h 17m 36s"
  },
  "memory": {
    "total": 268435456,
    "free": 178435456,
    "used": 90000000,
    "max": 4294967296
  },
  "system": {
    "availableProcessors": 8,
    "javaVersion": "21.0.10",
    "scalaVersion": "3.7.4"
  },
  "timestamp": 1738958400000
}
```

## Stats Included

### Uptime
- **milliseconds**: Time since server started (ms)
- **seconds**: Time since server started (seconds)
- **formatted**: Human-readable uptime (hours, minutes, seconds)


### Memory
- **total**: Total memory allocated to JVM (bytes)
- **free**: Free memory in JVM (bytes)
- **used**: Currently used memory (bytes)
- **max**: Maximum memory JVM can use (bytes)

### System
- **availableProcessors**: Number of CPU cores available
- **javaVersion**: Java runtime version
- **scalaVersion**: Scala compiler version

### Timestamp
- Current server timestamp (milliseconds since epoch)

## Use Cases

### Monitoring
Check if server is healthy and track resource usage:
```bash
watch -n 5 'curl -s http://localhost:8080/health | jq .'
```

### Debugging
Check server uptime and system info:
```bash
curl -s http://localhost:8080/health | jq '{status, uptime, system}'
```

### Alerting
Monitor memory usage:
```bash
curl -s http://localhost:8080/health | jq '.memory.used / .memory.max'
```

