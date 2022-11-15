package io.managed.services.test.quickstarts.contexts;

import io.managed.services.test.cli.CLI;
import lombok.Setter;

import java.util.Objects;

@Setter
public class RhoasCLIContext {

    private CLI cli;

    public CLI requireCLI() {
        return Objects.requireNonNull(cli);
    }
}
