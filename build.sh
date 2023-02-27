#!/bin/sh
mvn clean package -U -Dmaven.test.skip=true

cp prometheus-backend/target/prometheus-backend-1.18.3.jar .

zip -r prometheus.zip  ./prometheus-backend-1.18.3.jar   ./plugin.json
