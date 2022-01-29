import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StjsConfigService {
    private static final String ST_JS_PLUGIN_GROUP_ID = "org.st-js";
    private static final String ST_JS_PLUGIN_ARTIFACT_ID = "maven-plugin";
    private static final String STJS_DEPENDENCY = "st-js";
    private static final int SUCCESS_STATUS_CODE = 0;

    private final Project project;

    private boolean isStJsProject = false;

    private final Map<MavenProject, MavenPlugin> mavenProjectMavenPluginMap = new HashMap<>();

    public StjsConfigService(Project project) {
        this.project = project;
    }

    public void recognizeWhetherProjectIsOfStJsType() {
        Set<String> libraryNames = new HashSet<>();

        Queue<Module> visited = new LinkedList<>();
        Queue<Module> modulesToVisit = new LinkedList<>(Arrays.asList(ModuleManager.getInstance(project).getModules()));

        // Recursively visit all modules and collect libraries
        while (!modulesToVisit.isEmpty()) {
            Module module = modulesToVisit.poll();
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

            moduleRootManager.orderEntries().forEachLibrary(library -> {
                libraryNames.add(library.getName());
                return true;
            });
            visited.add(module);

            modulesToVisit.addAll(
                    Arrays.stream(moduleRootManager.getDependencies())
                            .filter(Predicate.not(visited::contains))
                            .collect(Collectors.toList()));
        }
        boolean isStJsPresent = libraryNames.stream().anyMatch(lib -> lib.contains(STJS_DEPENDENCY));

        if (isStJsPresent) {
            computeStJsPlugins();

            if (mavenProjectMavenPluginMap.isEmpty()) {

                NotificationGroupManager.getInstance()
                        .getNotificationGroup("st-js-notification-group")
                            .createNotification(
                                    "ST-JS has been detected detected, but ST-JS plugin is missing.\n" +
                                            "Hence feature of recompilation by the plugin is disabled",
                                    NotificationType.WARNING)
                            .notify(project);

            } else {
                isStJsProject = true;

                NotificationGroupManager.getInstance()
                        .getNotificationGroup("st-js-notification-group")
                            .createNotification(
                                    "Enjoy using ST-JS plugin",
                                    NotificationType.INFORMATION)
                            .notify(project);
            }
        }
    }

    public void recompileStJsInScopeOfModule(MavenProject module) {

        if (!isStJsProject) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("st-js-notification-group")
                    .createNotification(
                            "Project has no dependency on ST-JS",
                            NotificationType.WARNING)
                    .notify(project);

            return;
        }

        final MavenPlugin stjsPlugin = mavenProjectMavenPluginMap.get(module);

        if (stjsPlugin == null) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("st-js-notification-group")
                    .createNotification(
                            "ST-JS Plugin is not available in current module",
                            NotificationType.ERROR)
                    .notify(project);
            return;
        }

        final List<String> goals =
                stjsPlugin.getExecutions()
                        .stream()
                        .flatMap(e -> Optional.ofNullable(e.getGoals()).stream().flatMap(Collection::stream))
                        .map(v -> "org.st-js:maven-plugin:" + v )
                        .collect(Collectors.toList());

        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

        final MavenRunnerParameters compileMavenRunnerParameters =
                new MavenRunnerParameters(
                        true,
                        module.getDirectory(),
                        null,
                        List.of("compile"),
                        mavenProjectsManager.getExplicitProfiles());

        final MavenRunnerParameters mavenRunnerParameters =
                new MavenRunnerParameters(
                        true,
                        module.getDirectory(),
                        null,
                        goals,
                        mavenProjectsManager.getExplicitProfiles());

        Function<String, Runnable> errorMessageFunction =
                errorMessage ->
                        () -> ApplicationManager.getApplication().invokeLater(
                                () -> Messages.showErrorDialog(errorMessage, "ST-JS Plugin"));

        runMavenGoal(compileMavenRunnerParameters,
                () -> runMavenGoal(
                        mavenRunnerParameters,
                        () -> {},
                        errorMessageFunction.apply("ST-JS generation failed")),
                errorMessageFunction.apply("Java compilation failed"));
    }

    public void recompileStjsForWholeProject() {

        if (!isStJsProject) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("st-js-notification-group")
                    .createNotification(
                            "Project has no dependency on ST-JS",
                            NotificationType.WARNING)
                    .notify(project);

            return;
        }

        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

        if (mavenProjectsManager == null) {
            return;
        }
        final List<MavenProject> mavenProjects = mavenProjectsManager.getProjects();

        if (mavenProjects.isEmpty()) {
            return;
        }
        recompileStJsInScopeOfModule(mavenProjects.get(0));
    }

    private void runMavenGoal(MavenRunnerParameters mavenRunnerParameters, Runnable onSuccess, Runnable onError) {
        MavenRunConfigurationType.runConfiguration(project, mavenRunnerParameters, descriptor -> {
            ProcessHandler handler = descriptor.getProcessHandler();
            if (handler == null) {
                return;
            }
            handler.addProcessListener(new ProcessAdapter() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    final int exitCode = event.getExitCode();

                    if (exitCode == SUCCESS_STATUS_CODE) {
                        onSuccess.run();
                    } else {
                        onError.run();
                    }
                }
            });
        });
    }

    private void computeStJsPlugins() {
        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

        if (mavenProjectsManager == null) {
            return;
        }
        mavenProjectsManager.getProjects()
                .stream()
                .filter(Objects::nonNull)
                .forEach(mavenProject -> {
                    final MavenPlugin stjsPlugin =
                            mavenProject.findPlugin(ST_JS_PLUGIN_GROUP_ID, ST_JS_PLUGIN_ARTIFACT_ID);

                    if (stjsPlugin != null) {
                        mavenProjectMavenPluginMap.put(mavenProject, stjsPlugin);
                    }
                });
    }

}
