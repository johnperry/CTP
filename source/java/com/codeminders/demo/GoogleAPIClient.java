package com.codeminders.demo;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.log4j.Logger;

public class GoogleAPIClient {
    private final static Logger logger = Logger.getLogger(GoogleAPIClient.class);
    /**
     * Be sure to specify the name of your application. If the application name is
     * {@code null} or blank, the application will log a warning. Suggested format
     * is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "Codeminders-MIRCCTPDemo/1.0";

    /**
     * Directory to store user credentials.
     */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".store/google_viewer_auth");

    /**
     * Global instance of the {@link DataStoreFactory}. The best practice is to make
     * it a single globally shared instance across your application.
     */
    private static DataStoreFactory dataStoreFactory;

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport httpTransport;

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * OAuth 2.0 scopes.
     */
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/cloud-healthcare",
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email");

    private static Oauth2 oauth2;
    private static GoogleClientSecrets clientSecrets;

    /**
     * Instance of Google Cloud Resource Manager
     */
    private static CloudResourceManager cloudResourceManager;

    private boolean isSignedIn = false;
    private String accessToken;

    protected GoogleAPIClient() {
    }

    private static Credential authorize() throws Exception {
        // load client secrets
        clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(GoogleAPIClient.class.getResourceAsStream("/client_secrets.json")));
        if (clientSecrets.getDetails().getClientId().startsWith("Enter")
                || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            System.out.println("Enter Client ID and Secret from https://code.google.com/apis/console/ "
                    + "into src/main/resources/client_secrets.json");
            System.exit(1);
        }
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
                clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory).build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    public void signIn() throws Exception {
        if (!isSignedIn) {
            int tryCount = 0;
            Exception error;
            do {
                try {
                    tryCount++;
                    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                    dataStoreFactory = /*new FileDataStoreFactory(DATA_STORE_DIR); */new MemoryDataStoreFactory();
                    // authorization
                    Credential credential = authorize();
                    // set up global Oauth2 instance
                    oauth2 = new Oauth2.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
                            .build();

                    cloudResourceManager = new CloudResourceManager.Builder(httpTransport, JSON_FACTORY, credential)
                            .build();
                    accessToken = credential.getAccessToken();
                    // run commands
                    tokenInfo(accessToken);
                    userInfo();
                    error = null;
                    isSignedIn = true;
                } catch (Exception e) {
                    logger.error("Error occurred during authorization", e);
                    error = e;
                    e.printStackTrace();
                    logger.info("Retry authorization");
                    System.out.println("Retry authorization:");
                }
            } while (!isSignedIn && tryCount < 4);
            if (error != null) {
                throw error;
            }
        }
    }

    private static void tokenInfo(String accessToken) throws IOException {
        System.out.println("Validating token");
        Tokeninfo tokeninfo = oauth2.tokeninfo().setAccessToken(accessToken).execute();
        System.out.println(tokeninfo.toString());
        if (!tokeninfo.getAudience().equals(clientSecrets.getDetails().getClientId())) {
            System.err.println("ERROR: audience does not match our client ID!");
        }
    }

    private static void userInfo() throws IOException {
        System.out.println("Obtaining User Profile Information");
        Userinfoplus userinfo = oauth2.userinfo().get().execute();
        System.out.println(userinfo.toString());
    }

    public List<ProjectDescriptor> fetchProjects() throws Exception {
        signIn();
        List<ProjectDescriptor> result = new ArrayList<ProjectDescriptor>();
        CloudResourceManager.Projects.List request = cloudResourceManager.projects().list();
        ListProjectsResponse response;
        do {
            response = request.execute();
            if (response.getProjects() == null) {
                continue;
            }
            for (Project project : response.getProjects()) {
                result.add(new ProjectDescriptor(project.getName(), project.getProjectId()));
            }
            request.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);
        return result;
    }

    private String parseName(String name) {
        return name.substring(name.lastIndexOf("/") + 1);
    }

    private HttpResponse googleRequest(String url) throws Exception {
        signIn();
        HttpRequest request = httpTransport.createRequestFactory().buildGetRequest(new GenericUrl(url));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", Arrays.asList(new String[]{"Bearer " + accessToken}));
        request.setHeaders(headers);
        return request.execute();
    }

    public List<Location> fetchLocations(String projectId) throws Exception {
        signIn();
        String url = "https://healthcare.googleapis.com/v1alpha/projects/" + projectId + "/locations";
        String data = googleRequest(url).parseAsString();
        JsonParser parser = new JsonParser();
        JsonElement jsonTree = parser.parse(data);
        JsonArray jsonObject = jsonTree.getAsJsonObject().get("locations").getAsJsonArray();
        List<Location> list = StreamSupport.stream(jsonObject.spliterator(), false)
                .map(obj -> obj.getAsJsonObject())
                .map(obj -> {
                    return new Location(obj.get("name").getAsString(), obj.get("locationId").getAsString());
                })
                .collect(Collectors.toList());
        return list;
    }

    public List<String> fetchDatasets(String projectId, String locationId) throws Exception {
        signIn();
        String url = "https://healthcare.googleapis.com/v1alpha/projects/" + projectId + "/locations/" + locationId + "/datasets";
        String data = googleRequest(url).parseAsString();
        JsonParser parser = new JsonParser();
        JsonElement jsonTree = parser.parse(data);
        JsonArray jsonObject = jsonTree.getAsJsonObject().get("datasets").getAsJsonArray();
        return StreamSupport.stream(jsonObject.spliterator(), false)
                .map(obj -> obj.getAsJsonObject().get("name").getAsString())
                .map(this::parseName)
                .collect(Collectors.toList());
    }

    public List<String> fetchDicomstores(String projectId, String locationId, String dataset) throws Exception {
        signIn();
        String url = "https://healthcare.googleapis.com/v1alpha/projects/" + projectId + "/locations/" + locationId + "/datasets/" + dataset + "/dicomStores";
        String data = googleRequest(url).parseAsString();
        JsonParser parser = new JsonParser();
        JsonElement jsonTree = parser.parse(data);
        JsonArray jsonObject = jsonTree.getAsJsonObject().get("dicomStores").getAsJsonArray();
        List<String> result = StreamSupport.stream(jsonObject.spliterator(), false)
                .map(obj -> obj.getAsJsonObject().get("name").getAsString())
                .map(this::parseName)
                .collect(Collectors.toList());
        return result;
    }

}
