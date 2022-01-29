import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Objects;

public class RecompileStJsForCorrespondingModule extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        var mavenProject = getMavenModuleInContext(e);

        if (project == null) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("st-js-notification-group")
                    .createNotification(
                            "Unable to recognize project",
                            NotificationType.ERROR)
                    .notify();
            return;
        }

        if (mavenProject == null) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("st-js-notification-group")
                    .createNotification(
                            "Unable to recognize module, in which ST-JS has to be compiled",
                            NotificationType.ERROR)
                    .notify(project);
            return;
        }
        var configService = project.getService(StjsConfigService.class);

        configService.recompileStJsInScopeOfModule(mavenProject);
    }

    private MavenProject getMavenModuleInContext(AnActionEvent e) {
        var project = e.getProject();

        if (project == null) {
            return null;
        }
        var openedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (openedFile == null) {
            return null;
        }
        return findTheClosestModuleInProject(openedFile, project);
    }

    private MavenProject findTheClosestModuleInProject(VirtualFile virtualFile, Project project) {
        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

        if (mavenProjectsManager == null) {
            return null;
        }
        return mavenProjectsManager.findContainingProject(virtualFile);
    }

}
