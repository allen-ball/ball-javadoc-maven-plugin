package ball.maven.plugins.javadoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate javadoc options file for
 * {@code maven-javadoc-plugin}.
 *
 * {@maven.plugin.fields}
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-options-file",
      requiresDependencyResolution = RUNTIME,
      defaultPhase = GENERATE_SOURCES, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateOptionsFileMojo extends AbstractJavadocMojo {
    private static final Pattern JAR_ENTRY_PATTERN = Pattern.compile("^(package|element)[^-]*-list$");

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/javadoc-options")
    private File outputDirectory = null;

    @Parameter(required = false)
    private Link[] links = new Link[] { };

    @Parameter(required = false)
    private Offlinelink[] offlinelinks = new Offlinelink[] { };

    @Inject private MavenProject project = null;
    @Inject private MavenSession session = null;
    @Inject private ArtifactHandlerManager manager = null;
    @Inject private RepositorySystem system = null;
    @Inject private ArtifactResolver resolver = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        try {
            if (! isSkip()) {
                Set<URL> set = getLinkSet();
                Map<URL,List<Artifact>> map =
                    getResolvedOfflinelinkMap().entrySet().stream()
                    .collect(groupingBy(Map.Entry::getValue,
                                        mapping(Map.Entry::getKey, toList())));

                generateOutput(set, map);
            } else {
                log.info("Skipping javadoc options file generation.");
            }
        } catch (Throwable throwable) {
            log.error("{}", throwable.getMessage(), throwable);

            if (throwable instanceof MojoExecutionException) {
                throw (MojoExecutionException) throwable;
            } else if (throwable instanceof MojoFailureException) {
                throw (MojoFailureException) throwable;
            } else {
                throw new MojoExecutionException(throwable.getMessage(), throwable);
            }
        }
    }

    private Set<URL> getLinkSet() {
        Set<URL> set = new LinkedHashSet<>();

        for (Link link : links) {
            List<URL> list =
                project.getArtifacts().stream()
                .filter(link::include)
                .map(link::getUrl)
                .collect(toList());

            set.addAll(list);
        }

        return set;
    }

    private Map<Artifact,URL> getResolvedOfflinelinkMap() {
        Map<Artifact,URL> map = new TreeMap<>(Comparator.comparing(ArtifactUtils::versionlessKey));
        Set<Artifact> javadocs = getJavadocJarDependencyManagementSet();
        ProjectBuildingRequest request = getProjectBuildingRequest();

        for (Offlinelink offlinelink : offlinelinks) {
            Set<Artifact> set =
                javadocs.stream()
                .filter(offlinelink::include)
                .map(JavadocArtifact::new)
                .collect(toCollection(LinkedHashSet::new));

            for (Artifact artifact : set) {
                URL url = map.get(artifact);

                if (url == null) {
                    log.info("Resolving {}...", artifact);

                    try {
                        map.put(resolver.resolveArtifact(request, artifact).getArtifact(), offlinelink.getUrl(artifact));
                    } catch (Exception exception) {
                        log.warn("{}: {}", artifact, exception.getMessage());
                        log.debug("{}", exception);
                    }
                } else {
                    if (! Objects.equals(url, offlinelink.getUrl(artifact))) {
                        log.warn("{} matches {} but was previously resolved with {}",
                                 artifact, offlinelink, url);
                    }
                }
            }
        }

        return map;
    }

    private Set<Artifact> getJavadocJarDependencyManagementSet() {
        Set<Artifact> set =
            Stream.of(project.getDependencies(),
                      project.getDependencyManagement().getDependencies())
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(t -> isBlank(t.getClassifier()))
            .filter(t -> Objects.equals(t.getType(), "jar"))
            .filter(t -> (! isBlank(t.getVersion())))
            .map(t -> new JavadocArtifact(t.getGroupId(), t.getArtifactId(), t.getVersion()))
            .collect(toCollection(LinkedHashSet::new));

        return set;
    }

    private ProjectBuildingRequest getProjectBuildingRequest() {
        List<ArtifactRepository> repoList = getRemoteRepositories();
        Settings settings = session.getSettings();

        system.injectMirror(repoList, settings.getMirrors());
        system.injectProxy(repoList, settings.getProxies());
        system.injectAuthentication(repoList, settings.getServers());

        ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        request.setRemoteRepositories(repoList);

        return request;
    }

    private void generateOutput(Set<URL> set, Map<URL,List<Artifact>> map) throws IOException {
        Path parent = outputDirectory.toPath();

        Files.createDirectories(parent);

        Path options = parent.resolve("options");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(options, CREATE, WRITE, TRUNCATE_EXISTING))) {
            for (URL url : set) {
                out.println("-link");
                out.println(url);
            }

            for (Map.Entry<URL,List<Artifact>> entry : map.entrySet()) {
                List<Artifact> artifacts = entry.getValue();

                for (Artifact artifact : artifacts) {
                    Path location = parent.resolve(ArtifactUtils.versionlessKey(artifact));

                    Files.createDirectories(location);

                    try (JarFile jar = new JarFile(artifact.getFile())) {
                        List<JarEntry> entries =
                            jar.stream()
                            .filter(t -> JAR_ENTRY_PATTERN.matcher(t.getName()).matches())
                            .collect(toList());

                        if (! entries.isEmpty()) {
                            for (JarEntry jarEntry : entries) {
                                try (InputStream in = jar.getInputStream(jarEntry)) {
                                    Files.copy(in, location.resolve(jarEntry.getName()), REPLACE_EXISTING);
                                }
                            }

                            Path packageList = location.resolve("package-list");
                            Path elementList = location.resolve("element-list");

                            if (! Files.exists(packageList)) {
                                Files.copy(elementList, packageList);
                            } else if (! Files.exists(elementList)) {
                                Files.copy(packageList, elementList);
                            }

                            out.println("-linkoffline");
                            out.println(entry.getKey());
                            out.println(location);
                        } else {
                            log.warn("{}: Location directory is empty; skipping...", location);
                        }
                    }
                }
            }
        }
    }

    private class JavadocArtifact extends DefaultArtifact {
        public JavadocArtifact(String gav) { this(gav.split("[:]")); }

        public JavadocArtifact(String... gav) {
            super(gav.length > 0 ? gav[0] : EMPTY,
                  gav.length > 1 ? gav[1] : EMPTY,
                  gav.length > 2 ? gav[gav.length - 1] : EMPTY,
                  EMPTY, "jar", "javadoc", manager.getArtifactHandler("jar"));
        }

        public JavadocArtifact(Artifact artifact) {
            this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        }
    }
}
