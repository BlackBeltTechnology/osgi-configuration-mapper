package hu.blackbelt.configuration.mapper;

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
    Optional<URL> pid;
    Optional<URL> expression;

    public String getPidBaseName() {
        String fileName = Paths.get(template.getPath()).getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    @SneakyThrows(IOException.class)
    public BigInteger checkSum() {
        String str = Utils.readUrl(template);
        if (pid.isPresent()) {
            str += Utils.readUrl(pid.get());
        }

        if (expression.isPresent()) {
            str += Utils.readUrl(expression.get());
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
        if (getPid().isPresent() ? !getPid().get().equals(entry.getPid().orElse(null)) : entry.getPid().isPresent()) return false;
        return getExpression().isPresent() ? getExpression().get().equals(entry.getExpression().orElse(null)) : !entry.getExpression().isPresent();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getTemplate() != null ? getTemplate().hashCode() : 0);
        result = 31 * result + (getPid().isPresent() ? getPid().get().hashCode() : 0);
        result = 31 * result + (getExpression().isPresent() ? getExpression().get().hashCode() : 0);
        return result;
    }

    public String toString() {
        return String.format("ConfigurationEntry(template=%s, pid=%s, expression=%s)",
                this.getTemplate(), this.getPid().orElse(null), this.getExpression().orElse(null));
    }
}
