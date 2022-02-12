package ball.maven.plugins.javadoc;
/*-
 * ##########################################################################
 * Javadoc Maven Plugin
 * %%
 * Copyright (C) 2021, 2022 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

/**
 * {@code <link/>} parameter.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@Data @Slf4j
public class Link {
    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final Pattern DOT = Pattern.compile("[.]");

    private String artifact = null;
    private URL url = null;
    @Getter(lazy = true)
    private final StrictPatternIncludesArtifactFilter filter =
        new StrictPatternIncludesArtifactFilter(asList(artifact.split("[,\\p{Space}]+")));

    /**
     * See {@link StrictPatternIncludesArtifactFilter#include(Artifact)}.
     */
    public boolean include(Artifact artifact) {
        return getFilter().include(artifact);
    }

    /**
     * Method to return a {@link URL} substituting <code>{g}</code>,
     * <code>{a}</code>, and <code>{v}</code> with
     * {@link Artifact#getGroupId()}, {@link Artifact#getArtifactId()}, and
     * {@link Artifact#getVersion()}, respectively.
     *
     * @param   artifact        The {@link Artifact}.
     *
     * @return  The {@link URL} after substitution.
     */
    public URL getUrl(Artifact artifact) {
        URL url = getUrl();

        if (url != null && artifact != null) {
            TreeMap<String,String> map = new MapImpl(artifact);
            StringSubstitutor substitutor = new StringSubstitutor((StringLookup) map, "{", "}", '\\');

            substitutor.setEnableSubstitutionInVariables(true);

            String string = url.toString();

            try {
                url = new URL(substitutor.replace(string));
            } catch (MalformedURLException exception) {
                throw new IllegalArgumentException(exception);
            } catch (NoSuchElementException exception) {
                throw new IllegalArgumentException(String.format("No value defined for '%s' in '%s'",
                                                                 exception.getMessage(), string));
            }
        }

        return url;
    }

    private class MapImpl extends TreeMap<String,String> implements StringLookup {
        private static final long serialVersionUID = -1285467833760198210L;

        public MapImpl(Artifact artifact) {
            super(String.CASE_INSENSITIVE_ORDER);

            put("groupId", artifact.getGroupId());
            put("artifactId", artifact.getArtifactId());
            put("version", artifact.getBaseVersion());

            put("g", get("groupId"));
            put("a", get("artifactId"));
            put("v", get("version"));

            String version = get("version");

            for (String key : asList("major", "minor", "micro", "patch")) {
                Matcher matcher = NUMBER.matcher(version);

                if (matcher.lookingAt()) {
                    put(key, matcher.group());
                    version = version.substring(matcher.group().length());
                } else {
                    break;
                }

                matcher = DOT.matcher(version);

                if (matcher.lookingAt()) {
                    version = version.substring(matcher.group().length());
                    continue;
                } else {
                    break;
                }
            }
        }

        @Override
        public String lookup(String key) {
            String value = get(key);

            if (value == null) {
                String prefix = key.toLowerCase();
                Set<String> set =
                    tailMap(key).entrySet().stream()
                    .filter(t -> t.getKey().startsWith(prefix))
                    .map(t -> t.getValue())
                    .collect(toSet());

                if (set.size() == 1) {
                    value = set.iterator().next();
                } else {
                    throw new NoSuchElementException(key);
                }
            }

            return value;
        }
    }
}
