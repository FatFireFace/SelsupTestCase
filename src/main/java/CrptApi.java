import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHttpResponse;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

public class CrptApi {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Semaphore semaphore;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        long period = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(this::resetSemaphore, period, period, TimeUnit.MILLISECONDS);
        this.httpClient = (HttpClient) HttpClients.createDefault();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void createDocument(Document document, String signature) {
        try {
            semaphore.acquire();
            sendPostRequest(document, signature);
            System.out.println("Document created successfully.");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }

    private void sendPostRequest(Document document, String signature) throws IOException {
        String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");

        RequestBody requestBody = new RequestBody(document, signature);
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        StringEntity entity = new StringEntity(jsonBody);
        httpPost.setEntity(entity);
        ProtocolVersion protocolVersion = new ProtocolVersion("HTTPS", 1, 1);
        StatusLine statusLine = new StatusLine(protocolVersion, 200, "OK");

        try (HttpResponse response =  new BasicHttpResponse(statusLine)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                System.err.println("Failed to create document. Status code: " + statusCode);
                // Handle error response if needed
            }
        }
    }

    private void resetSemaphore() {
        semaphore.drainPermits();
    }

    private static class RequestBody {
        private final Document description;
        private final String signature;

        public RequestBody(Document description, String signature) {
            this.description = description;
            this.signature = signature;
        }

    }

    private enum DocType{
        LP_INTRODUCE_GOODS
    }
    private static class Document {
        private String participantInn;
        private String doc_id;
        private String doc_status;
        private DocType doc_type;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private Date production_date;
        private String production_type;
        private List<Product> products;
        private Date reg_date;
        private String reg_number;


    }

    private static class Product{
        private String certificate_document;
        private Date certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 10);
        Document document = new Document();
        //method call
        api.createDocument(document, "signature_here");
    }
}