import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class StJsProjectRecognizer implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        // Run when indexation is completed
        DumbService.getInstance(project).runWhenSmart(() -> {
            StjsConfigService configService = project.getService(StjsConfigService.class);

            configService.recognizeWhetherProjectIsOfStJsType();
        });
    }

}
