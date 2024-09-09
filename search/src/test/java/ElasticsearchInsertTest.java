import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

public class ElasticsearchInsertTest {

    public static void main(String[] args) throws IOException{
        try(RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost",9200,"http"))
        )){
            BulkRequest bulkRequest = new BulkRequest();
            int batchSize = 100;
            long start = System.currentTimeMillis();
            int count = 1000005;
            for(int i=1;i<=10000000;i++){
                String traceId = "trace_" + count++;
                StringBuilder spansBuilder = new StringBuilder("[");
                for(int j=1;j<=10;j++){
                    spansBuilder.append("{ \"spanId\":").append(j).append(", ")
                            .append("\"traceId\":\"").append(traceId).append("\",")
                            .append("\"parentSpanId\":").append(j-1).append(", ")
                            .append("\"operationName\":\"operation_").append(j).append(", ")
                            .append("\"startTime\": ").append(1725641066535L + j * 1000).append(", ")
                            .append("\"endTime\": ").append(1725641066535L + j * 1500).append(" }");
                    if(j<10){
                        spansBuilder.append(", ");
                    }
                }
                spansBuilder.append("]");
                String jsonData="{ \"traceId\": \"" + traceId + "\", "
                        + "\"segmentId\": -1, "
                        + "\"serviceName\": \"service_" + i + "\", "
                        + "\"serviceId\": \"service_" + i + "\", "
                        + "\"spans\": " + spansBuilder.toString() + ", "  // 添加伪造的 spans 数据
                        + "\"startTime\": 1725641066535, "
                        + "\"endTime\": 1725641067000, "
                        + "\"peer\": \"peer_" + i + "\" }";

                IndexRequest test = new IndexRequest("test").source(jsonData,XContentType.JSON);
                bulkRequest.add(test);
                if(i % batchSize == 0){
                    BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                    if(bulk.hasFailures()){
                        System.out.println("Bulk insert failed: " + bulk.buildFailureMessage());

                    }
                    bulkRequest = new BulkRequest();
                }


            }
            if(bulkRequest.numberOfActions()>0){
                BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                if(bulk.hasFailures()){
                    System.out.println("Bulk insert failed: " + bulk.buildFailureMessage());
                }
            }
            long end = System.currentTimeMillis();
            System.out.println("Inserted 9000000 records into ElasticSearch in " + (end-start) + "ms");
        }
    }
}
