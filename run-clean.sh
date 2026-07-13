#!/bin/bash
# Priority: command line arg > environment variable > default 8081
PORT=${1:-${SERVER_PORT:-8081}}
PID=$(lsof -t -i:$PORT)

if [ -z "$PID" ]; then
    echo "✅ Port $PORT is free."
else
    echo "⚠️ Port $PORT is occupied by PID $PID."
    PROC_NAME=$(ps -p $PID -o comm=)
    if [[ "$PROC_NAME" == *"java"* ]] || [[ "$PROC_NAME" == *"mvnw"* ]]; then
        echo "🔥 Terminating ghost Java process..."
        kill -15 $PID
        sleep 2
        kill -9 $PID 2>/dev/null
    else
        echo "❌ Port $PORT is owned by $PROC_NAME (Non-Java). Please free it manually."
        exit 1
    fi
fi

echo "🚀 Starting app on port $PORT..."
# Pass the port to Maven to ensure it overrides any env vars or properties
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=$PORT"
