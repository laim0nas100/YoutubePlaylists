/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lt.lb.youtubeplaylists;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import lt.lb.commons.F;
import lt.lb.commons.SafeOpt;
import lt.lb.commons.iteration.PagedIteration;
import lt.lb.commons.parsing.StringOp;

/**
 *
 * @author Lemmin
 */
public class API {

    private static final String CLIENT_SECRETS = "/credentials/credential.json";
    private static final Collection<String> SCOPES
            = Arrays.asList("https://www.googleapis.com/auth/youtube.readonly");

    public static final String APPLICATION_NAME = "Youtube playlist extractor";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Create an authorized Credential object.
     *
     * @param httpTransport
     * @return an authorized Credential object.
     * @throws IOException
     */
    public Credential authorize(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets.
        InputStream in = Main.class.getResourceAsStream(CLIENT_SECRETS);

        
        GoogleClientSecrets clientSecrets
                = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        // Build flow and trigger user authorization request.
        FileDataStoreFactory factory = new FileDataStoreFactory(new File("cred"));
        
        GoogleAuthorizationCodeFlow flow
                = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(factory)
                        .build();

        Credential credential
                = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
     * Build and return an authorized API client service.
     *
     * @return an authorized API client service
     * @throws GeneralSecurityException, IOException
     */
    public YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public Iterable<PlaylistItem> getPlaylist(String playlistID) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();
        PagedIteration<PlaylistItemListResponse, PlaylistItem> iteration = new PagedIteration<PlaylistItemListResponse, PlaylistItem>() {
            @Override
            public PlaylistItemListResponse getFirstPage() {
                return F.unsafeCall(() -> {
                    return youtubeService.playlistItems()
                            .list("snippet,contentDetails")
                            .setMaxResults(50L)
                            .setPlaylistId(playlistID)
                            .execute();
                });

            }

            @Override
            public Iterator<PlaylistItem> getItems(PlaylistItemListResponse info) {
                return info.getItems().iterator();
            }

            @Override
            public PlaylistItemListResponse getNextPage(PlaylistItemListResponse info) {
                return F.unsafeCall(() -> {
                    return youtubeService.playlistItems()
                            .list("snippet,contentDetails")
                            .setMaxResults(50L)
                            .setPlaylistId(playlistID)
                            .setPageToken(info.getNextPageToken())
                            .execute();
                });

            }

            @Override
            public boolean hasNextPage(PlaylistItemListResponse info) {
                return StringOp.isNoneEmpty(info.getNextPageToken());
            }
        };

        return iteration;
    }

    /*
    String uploadsPlaylist = execute.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
     */
    public SafeOpt<String> getUploadsPlaylistFromUsername(String username) throws GeneralSecurityException, IOException {
        YouTube youtubeService = getService();
        return SafeOpt.of(username)
                .map(m -> {
                    return youtubeService.channels().list("contentDetails").setForUsername(m).execute();
                })
                .map(m -> m.getItems().get(0))
                .map(m -> m.getContentDetails())
                .map(m -> m.getRelatedPlaylists())
                .map(m -> m.getUploads());

    }

}
