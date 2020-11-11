package lt.lb.youtubeplaylists;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import com.google.api.services.youtube.model.PlaylistItem;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import lt.lb.commons.F;
import lt.lb.commons.SafeOpt;
import lt.lb.commons.containers.values.Value;
import lt.lb.commons.func.unchecked.UnsafeRunnable;
import lt.lb.commons.func.unchecked.UnsafeSupplier;
import lt.lb.commons.iteration.ReadOnlyIterator;
import lt.lb.commons.javafx.FX;
import lt.lb.commons.javafx.FXDefs;
import lt.lb.commons.javafx.fxrows.FXDrows;
import lt.lb.commons.javafx.fxrows.FXSync;
import lt.lb.commons.javafx.fxrows.FXValid;
import lt.lb.commons.javafx.scenemanagement.Frame;
import lt.lb.commons.javafx.scenemanagement.MultiStageManager;
import lt.lb.commons.javafx.scenemanagement.StageFrame;
import lt.lb.commons.javafx.scenemanagement.frames.FrameDecorate;
import lt.lb.commons.javafx.scenemanagement.frames.WithDecoration;
import lt.lb.commons.javafx.scenemanagement.frames.WithDefaultStageProperties;
import lt.lb.commons.javafx.scenemanagement.frames.WithFrameTypeMemoryPosition;
import lt.lb.commons.javafx.scenemanagement.frames.WithFrameTypeMemorySize;
import lt.lb.commons.javafx.scenemanagement.frames.WithIcon;
import org.tinylog.Logger;

public class Main {

    public static MultiStageManager sm;
    public static WithFrameTypeMemoryPosition positionInfo = new WithFrameTypeMemoryPosition();
    public static WithFrameTypeMemorySize sizeInfo = new WithFrameTypeMemorySize();
    public static ClassLoader cLoader = Main.class.getClassLoader();
    public static API api = new API();

    public static void main(String[] args)
            throws GeneralSecurityException, IOException, GoogleJsonResponseException {
        Image icon = new Image(cLoader.getResourceAsStream("img/icon.png"));
        sm = new MultiStageManager(
                new WithDefaultStageProperties(c -> {
                    c.setHeight(600);
                    c.setWidth(800);
                }),
                positionInfo,
                sizeInfo,
                new WithIcon(icon),
                new WithDecoration(FrameDecorate.FrameState.CLOSE, d -> {

                })
        );


        newRows(API.APPLICATION_NAME, generateMain());

    }

    public static FXDrows generateMain() {
        FXDrows rows = FXDefs.fxrows();
        Value<String> playlistID = new Value<>();
        
        FXDrows rows1 = FXDefs.fxrows();
        FXSync.TextFieldSync<String> fieldID = FXSync.ofTextField(playlistID);
        fieldID.addPersistValidation(FXValid.valNotBlank());
        rows1.getNew()
                .addLabel("Playlist ID:")
                .addFxSync(fieldID)
                .addButton("Crawl", ev -> {
                    rows1.syncManagedFromDisplay();
                    if (rows1.invalidPersist()) {
                        return;
                    }
                    rows1.syncPersist();

                    withAlert(() -> {
                        Iterable<PlaylistItem> playlist = api.getPlaylist(playlistID.get());
                        displayPlaylistIDS("Generated from " + playlistID.get(), playlist);
                    });

                })
                .withPreferedColspan(4, 7,3)
                .display();

        FXDrows rows2 = FXDefs.fxrows();
        Value<String> username = new Value<>();
        FXSync.TextFieldSync<String> fieldUsername = FXSync.ofTextField(username);
        fieldUsername.addPersistValidation(FXValid.valNotBlank());
        rows2.getNew()
                .addLabel("Usename:")
                .addFxSync(fieldUsername)
                .addButton("Crawl", ev -> {
                    rows2.syncManagedFromDisplay();
                    if (rows2.invalidPersist()) {
                        return;
                    }
                    rows2.syncPersist();

                    withAlert(() -> {
                        SafeOpt<String> get = api.getUploadsPlaylistFromUsername(username.get());
                        if(get.isEmpty()){
                            throw new RuntimeException("Failed to get uploads from "+username.get());
                        }
                        Iterable<PlaylistItem> playlist = api.getPlaylist(get.get());
                        displayPlaylistIDS("Generated from " + username.get() + " uploads", playlist);
                    });

                })
                .withPreferedColspan(4, 7,3)
                .display();

        
        rows.composeRowsLast(rows1);
        rows.composeRowsLast(rows2);
        rows.getNew()
                .addButton("Reset", eh->{
                    rows.clearInvalidationPersist(null);
                    fieldUsername.setManaged(null);
                    fieldID.setManaged(null);
                    rows.syncDisplay();
                }).display();
        return rows;

    }

    public static TextArea generateTextArea(String text) {

        TextArea area = new TextArea(text);

        GridPane.setHgrow(area, Priority.ALWAYS);
        return area;

    }

    public static void displayPlaylistIDS(String title, Iterable<PlaylistItem> items) {
        ReadOnlyIterator<String> mapped = ReadOnlyIterator.of(items).map(m -> {
            String id = m.getContentDetails().getVideoId();
            return genID(id);
        });

        String collect = mapped.toStream().collect(Collectors.joining("\n"));

        newFrame(title, () -> generateTextArea(collect));
    }

    public static void newFrame(String title, UnsafeSupplier<Parent> node) {
        withAlert(() -> {
            Value<Frame> frameSup = new Value<>();
            StageFrame frame = sm.newStageFrame(title, node, d -> sm.closeFrame(d.getID()));
            frameSup.accept(frame);
            frame.show();
        });
    }

    public static void newRows(String title, FXDrows rows) {

        newFrame(title, () -> rows.grid);
        rows.syncManagedFromPersist();
        rows.viewUpdate();
    }

    public static void withAlert(UnsafeRunnable run) {
        Optional<Throwable> ex = F.checkedRun(run);
        if (ex.isPresent()) {

            Logger.error(ex);
            FX.submit(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText(ex.get().getMessage());
                alert.showAndWait();
            });

        }

    }

    public static String genID(String id) {
        return "https://www.youtube.com/watch?v=" + id;
    }

}
