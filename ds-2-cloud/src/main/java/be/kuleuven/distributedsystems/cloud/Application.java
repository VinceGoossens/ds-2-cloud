package be.kuleuven.distributedsystems.cloud;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.*;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.HypermediaWebClientConfigurer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.Objects;

@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@SpringBootApplication
public class Application {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException {
        System.setProperty("server.port", System.getenv().getOrDefault("PORT", "8080"));

        ApplicationContext context = SpringApplication.run(Application.class, args);

        // TODO: (level 2) load this data into Firestore
        String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());

        initializeTopic();
    }

    public static void initializeTopic() throws IOException {
        TransportChannelProvider channelProvider = channelProvider();
        CredentialsProvider credentialsProvider = credentialsProvider();
        TopicAdminClient topicAdminClient =
                TopicAdminClient.create(
                        TopicAdminSettings.newBuilder()
                                .setTransportChannelProvider(channelProvider)
                                .setCredentialsProvider(credentialsProvider)
                                .build());

        TopicName topicName = TopicName.of(projectId(), "confirmQuotes");
        try {
            Topic topic =
                    topicAdminClient.createTopic(
                            Topic.newBuilder()
                                    .setName(topicName.toString())
                                    .build());

            createSubscription(channelProvider, credentialsProvider, topicName);
        } catch (Exception e) {
            System.out.println("Topic already exists");
        }
    }

    public static void createSubscription(TransportChannelProvider channelProvider, CredentialsProvider credentialsProvider, TopicName topicName) throws IOException {
            SubscriptionAdminClient subscriptionAdminClient =
                    SubscriptionAdminClient.create(
                            SubscriptionAdminSettings.newBuilder()
                                    .setTransportChannelProvider(channelProvider)
                                    .setCredentialsProvider(credentialsProvider)
                                    .build());

            PushConfig pushConfig = PushConfig.newBuilder()
                    .setPushEndpoint("http://localhost:8080/pubsub/subscription")
                    .build();
        try {
            SubscriptionName subscriptionName = SubscriptionName.of(projectId(), "confirmQuotes");

            subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 60);
        } catch (Exception e) {
            System.out.println("Subscription already exists");
        }
    }

    @Bean
    public boolean isProduction() {
        return Objects.equals(System.getenv("GAE_ENV"), "standard");
    }

    @Bean
    public static String projectId() {
        return "demo-distributed-systems-kul";
    }

    /*
     * You can use this builder to create a Spring WebClient instance which can be used to make REST-calls.
     */
    @Bean
    WebClient.Builder webClientBuilder(HypermediaWebClientConfigurer configurer) {
        return configurer.registerHypermediaTypes(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)));
    }

    @Bean
    HttpFirewall httpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    @Bean
    static TransportChannelProvider channelProvider() {
        return FixedTransportChannelProvider.create(GrpcTransportChannel
                .create(ManagedChannelBuilder
                        .forTarget("localhost:8083")
                        .usePlaintext()
                        .build()));
    }

    @Bean
    static CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }

    @Bean
    public Firestore db() {
        return FirestoreOptions.getDefaultInstance()
                .toBuilder()
                .setEmulatorHost("localhost:8084")
                .setProjectId(projectId())
                .setCredentials(new FirestoreOptions.EmulatorCredentials())
                .build()
                .getService();
    }
}
