package io.hollan.functions;

import java.util.*;

import com.google.gson.JsonObject;
import com.microsoft.azure.serverless.functions.annotation.*;
import com.microsoft.azure.serverless.functions.*;

import org.json.*;

import com.microsoft.azure.cognitiveservices.computervision.*;
import com.microsoft.azure.cognitiveservices.computervision.implementation.ComputerVisionAPIImpl;
import com.microsoft.azure.cognitiveservices.computervision.implementation.ImageAnalysisInner;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    @FunctionName("analyze")
    public static void analyze(
            @BlobTrigger(path = "images", name = "image", connection = "AzureWebJobsStorage", dataType = "binary") byte[] image,
            @DocumentDBOutput(name = "outputDocument", databaseName = "images", collectionName = "analysis", connection = "CosmosDbConnection") OutputBinding<String> outputDocument,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Create a client.
        ComputerVisionAPIImpl client = Function.getClient(apiKey);
        client.withAzureRegion(AzureRegions.WESTUS2);
        List<VisualFeatureTypes> visualFeatureTypes = new ArrayList<VisualFeatureTypes>();
        visualFeatureTypes.add(VisualFeatureTypes.DESCRIPTION);
        visualFeatureTypes.add(VisualFeatureTypes.CATEGORIES);
        visualFeatureTypes.add(VisualFeatureTypes.COLOR);
        visualFeatureTypes.add(VisualFeatureTypes.FACES);
        visualFeatureTypes.add(VisualFeatureTypes.IMAGE_TYPE);
        visualFeatureTypes.add(VisualFeatureTypes.TAGS);

        ImageAnalysisInner result = client.analyzeImageInStream(
                image,
                visualFeatureTypes,
                null,
                null);

        JSONObject document = new JSONObject();
        document.put("id", java.util.UUID.randomUUID());

        if(result.description() != null &&
                result.description().captions() != null) {
            context.getLogger().info("The image can be described as: %s\n" + result.description().captions().get(0).text());
            document.put("description", result.description().captions().get(0).text());
        }


        for(ImageTag tag : result.tags())
        {
            context.getLogger().info(
                    String.format("%s\t\t%s", tag.name(), tag.confidence()));
            document.append("tags", new JSONObject(String.format("{\"name\": \"%s\", \"confidence\": \"%s\" }", tag.name(), tag.confidence())));
        }

        context.getLogger().info(
                String.format("\nThe primary colors of this image are: %s", String.join(", ", result.color().dominantColors())));

        document.put("primaryColors", String.join(", ", result.color().dominantColors()));

        context.getLogger().info("Document: \n" + document.toString());

        outputDocument.setValue(document.toString());
    }


    public static ComputerVisionAPIImpl getClient(final String subscriptionKey) {
        return new ComputerVisionAPIImpl(
                "https://westus2.api.cognitive.microsoft.com/vision/v1.0/",
                new ServiceClientCredentials() {
                    @Override
                    public void applyCredentialsFilter(OkHttpClient.Builder builder) {
                        builder.addNetworkInterceptor(
                                new Interceptor() {
                                    @Override
                                    public Response intercept(Interceptor.Chain chain) throws IOException {
                                        Request request = null;
                                        Request original = chain.request();
                                        // Request customization: add request headers
                                        Request.Builder requestBuilder = original.newBuilder()
                                                .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey);
                                        request = requestBuilder.build();
                                        return chain.proceed(request);
                                    }
                                });
                    }
                });
    }

    public static String apiKey = System.getenv("CognitiveServicesApiKey");
}
