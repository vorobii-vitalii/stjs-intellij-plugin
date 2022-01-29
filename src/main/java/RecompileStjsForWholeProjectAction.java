import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class RecompileStjsForWholeProjectAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();

        if (project == null) {
            // TODO: Add Log
            return;
        }

        StjsConfigService configService = project.getService(StjsConfigService.class);

        configService.recompileStjsForWholeProject();
    }

}
