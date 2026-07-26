package mlogix.problem;

import arc.struct.Seq;
import arc.util.Log;

public class ProblemCollector {
    public final Seq<Problem> errors = new Seq<>();
    public final Seq<Problem> warnings = new Seq<>();

    public ProblemCollector() {}

    public boolean hasError() {
        return !errors.isEmpty();
    }

    public int errorNum() {
        return errors.size;
    }

    public int warningNum() {
        return warnings.size;
    }

    public void addError(Problem error) {
        errors.add(error);
    }

    public void addWarning(Problem warning) {
        warnings.add(warning);
    }

    public void printError() {
        errors.forEach(e -> Log.err(e.toString()));
    }

    public void printWarning() {
        warnings.forEach(w -> Log.warn(w.toString()));
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }
}
