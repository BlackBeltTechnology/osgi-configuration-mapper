package hu.blackbelt.configuration.mapper;

/*-
 * #%L
 * OSGi Configuration mapper
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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
 * #L%
 */

import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Contains an generatable entry definition.
 */
@Builder
@Getter
@SuppressWarnings("checkstyle:missingctor")
public class ConfigurationEntry implements Serializable {
    URL template;
    Optional<URL> spec;
    Optional<String> instance;

    public String getPidBaseName() {
        String fileName = Paths.get(template.getPath()).getFileName().toString();
        if (instance.isPresent()) {
            final String withoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
            return withoutExtension.substring(0, withoutExtension.lastIndexOf("-" + instance.get()));
        } else {
            return fileName.substring(0, fileName.lastIndexOf("."));
        }
    }

    @SneakyThrows(IOException.class)
    public BigInteger checkSum() {
        String str = Utils.readUrl(template);
        if (spec.isPresent()) {
            str += Utils.readUrl(spec.get());
        }

        if (instance.isPresent()) {
            str += instance;
        }
        return Utils.sha1(str);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigurationEntry)) return false;
        if (!super.equals(o)) return false;

        ConfigurationEntry entry = (ConfigurationEntry) o;

        if (getTemplate() != null ? !getTemplate().equals(entry.getTemplate()) : entry.getTemplate() != null) return false;
        if (getSpec().isPresent() ? !getSpec().get().equals(entry.getSpec().orElse(null)) : entry.getSpec().isPresent()) return false;
        return getInstance().isPresent() ? getInstance().get().equals(entry.getInstance().orElse(null)) : !entry.getInstance().isPresent();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getTemplate() != null ? getTemplate().hashCode() : 0);
        result = 31 * result + (getSpec().isPresent() ? getSpec().get().hashCode() : 0);
        result = 31 * result + (getInstance().isPresent() ? getInstance().get().hashCode() : 0);
        return result;
    }

    public String toString() {
        return String.format("ConfigurationEntry(template=%s, spec=%s, instance=%s)",
                this.getTemplate(), this.getSpec().orElse(null), this.getInstance().orElse(null));
    }
}
