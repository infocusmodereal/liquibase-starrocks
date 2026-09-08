import java.io.*;
import java.nio.file.*;
import java.security.Security;
import java.util.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.*;

/** Verifies detached signatures using only public key packets. Never decrypts or emits keys. */
class VerifySignatures {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Map<Long, PGPPublicKey> keys = new HashMap<>();
        try (InputStream input = PGPUtil.getDecoderStream(Files.newInputStream(Path.of(args[0])))) {
            PGPObjectFactory objects = new PGPObjectFactory(input, new JcaKeyFingerprintCalculator());
            Object object;
            while ((object = objects.nextObject()) != null) {
                Iterator<PGPPublicKey> iterator;
                if (object instanceof PGPSecretKeyRing ring) iterator = ring.getPublicKeys();
                else if (object instanceof PGPPublicKeyRing ring) iterator = ring.getPublicKeys();
                else continue;
                iterator.forEachRemaining(key -> keys.put(key.getKeyID(), key));
            }
        }
        int count = 0;
        try (var files = Files.walk(Path.of(args[1]))) {
            for (Path signatureFile : files.filter(p -> p.toString().endsWith(".asc")).toList()) {
                PGPSignature signature;
                try (InputStream input = PGPUtil.getDecoderStream(Files.newInputStream(signatureFile))) {
                    var factory = new PGPObjectFactory(input, new JcaKeyFingerprintCalculator());
                    signature = ((PGPSignatureList) factory.nextObject()).get(0);
                }
                PGPPublicKey key = keys.get(signature.getKeyID());
                if (key == null) throw new IllegalStateException("No matching public signing key");
                signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
                Path artifact = Path.of(signatureFile.toString().replaceFirst("\\.asc$", ""));
                try (InputStream input = Files.newInputStream(artifact)) {
                    byte[] block = new byte[8192];
                    int length;
                    while ((length = input.read(block)) != -1) signature.update(block, 0, length);
                }
                if (!signature.verify()) throw new IllegalStateException("Invalid signature: " + artifact.getFileName());
                System.out.println("Verified signature: " + artifact.getFileName());
                count++;
            }
        }
        if (count != 4) throw new IllegalStateException("Expected exactly four signed artifacts");
    }
}
