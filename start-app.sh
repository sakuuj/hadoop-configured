./gradlew clean jar
docker build -f Dockerfile-app --tag 'app' .
docker run --name app --network hadoop_lab_hdnet app