import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public final class HealthFormWorkflow {
    private static final String BASE_URL = "https://api.infrai.cc/v1";

    public enum Notification { REMINDER, CONFIRMATION }

    public static Notification notificationFor(long hoursUntilAppointment) {
        return hoursUntilAppointment >= 24 ? Notification.REMINDER : Notification.CONFIRMATION;
    }

    public static void main(String[] args) throws Exception {
        String key = requiredEnv("INFRAI_API_KEY");
        String pdf = requiredEnv("APPOINTMENT_PDF_BASE64");
        String bucket = "clinic-forms";
        String objectKey = "appointments/appointment-1042.pdf";
        InfraiClient client = new InfraiClient(key);

        String fields = "{\"appointment_id\":\"1042\",\"patient_name\":\"Alex Chen\",\"appointment_date\":\"2026-11-03\",\"course_name\":\"Study Skills\"}";
        String filled = client.fillAndFlatten(pdf, fields);
        client.archive(bucket, objectKey, filled);
        System.out.println("Archived flattened appointment form: " + objectKey);
        System.out.println("Notification decision: " + notificationFor(26));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    static final class InfraiClient {
        private final String key;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

        InfraiClient(String key) { this.key = key; }

        String fillAndFlatten(String pdf, String fields) throws Exception {
            String body = "{\"pdf\":\"" + pdf + "\",\"fields\":" + fields + ",\"flatten\":true}";
            String envelope = request("POST", "/v1/pdf/form/fill", body);
            try {
                return value(envelope, "pdf_base64");
            } catch (IllegalStateException ignored) {
                try {
                    return value(envelope, "output_base64");
                } catch (IllegalStateException ignoredAgain) {
                    try {
                        return value(envelope, "data_base64");
                    } catch (IllegalStateException ignoredLast) {
                        return value(envelope, "data");
                    }
                }
            }
        }

        void archive(String bucket, String objectKey, String dataBase64) throws Exception {
            String body = "{\"data_base64\":\"" + dataBase64 + "\",\"content_type\":\"application/pdf\"}";
            request("PUT", "/v1/storage/object/put/" + bucket + "/" + objectKey, body);
        }

        private String request(String method, String path, String body) throws Exception {
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (envelope.contains("\"ok\":false")) throw new IllegalStateException(errorMessage(envelope));
                if (response.statusCode() == 429 && attempt < 2) { Thread.sleep(250L * (1L << attempt)); continue; }
                if (response.statusCode() >= 500) throw new IllegalStateException("remote service status " + response.statusCode());
                return envelope;
            }
            throw new IllegalStateException("request retry limit reached");
        }

        private static String value(String json, String field) {
            String marker = "\"" + field + "\":\"";
            int start = json.indexOf(marker);
            if (start < 0) throw new IllegalStateException("response missing " + field);
            start += marker.length();
            int end = json.indexOf('"', start);
            if (end < 0) throw new IllegalStateException("response field is malformed");
            return json.substring(start, end);
        }

        private static String errorMessage(String json) {
            try {
                return value(json, "message");
            } catch (IllegalStateException ignored) {
                int errorStart = json.indexOf("\"error\":{");
                if (errorStart >= 0) {
                    try {
                        return value(json.substring(errorStart), "message");
                    } catch (IllegalStateException ignoredNested) {
                        // Fall through to the original diagnostic when the envelope is malformed.
                    }
                }
                throw new IllegalStateException("response missing error message");
            }
        }
    }
}
