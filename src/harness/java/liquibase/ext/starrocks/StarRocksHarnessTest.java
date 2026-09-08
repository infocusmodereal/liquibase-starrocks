package liquibase.ext.starrocks;

import liquibase.harness.change.ChangeObjectTests;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses(ChangeObjectTests.class)
public class StarRocksHarnessTest {
}
