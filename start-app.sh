./gradlew clean jar
docker build -f Dockerfile-app --tag 'app' .
docker run --name app --network hadoop-test_hdnet app