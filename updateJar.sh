BUCKET_NAME="s3://matsim-jobs-input-xxxxxxxxxxxx"

cd matsim-aws-setup
mvn clean package -DskipTests=true
aws s3 cp ./target/matsim-aws-setup-1.0-SNAPSHOT-fat.jar ${BUCKET_NAME}/jars/equil.jar
