# Initialize keys and certificates for rootCA and intermediate CA
java -cp target/classes/:target/mc-identityregistry-core-0.0.1-SNAPSHOT/WEB-INF/lib/bcpkix-jdk15on-1.50.jar:target/mc-identityregistry-core-0.0.1-SNAPSHOT/WEB-INF/lib/bcprov-ext-jdk15on-1.54.jar:target/mc-identityregistry-core-0.0.1-SNAPSHOT/WEB-INF/lib/bcprov-jdk15on-1.50.jar  net.maritimecloud.identityregistry.utils.CertificateUtil
