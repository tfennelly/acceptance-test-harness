package core;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.Bug;
import org.jenkinsci.test.acceptance.junit.Since;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.MatrixBuild;
import org.jenkinsci.test.acceptance.po.MatrixConfiguration;
import org.jenkinsci.test.acceptance.po.MatrixProject;
import org.jenkinsci.test.acceptance.po.MatrixRun;
import org.jenkinsci.test.acceptance.po.StringParameter;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonMap;

/**
 Feature: Use multi configuration job
   As a Jenkins user
   I want to configure and run multi configuration jobs
 */
public class MatrixTest extends AbstractJUnitTest {
    MatrixProject job;

    @Before
    public void setUp() {
        job = jenkins.jobs.create(MatrixProject.class);
    }

    /**
     Scenario: Run configurations sequentially
       Given a matrix job
       When I configure the job
       And I configure user axis "user_axis" with values "axis1 axis2 axis3"
       And I configure to run configurations sequentially
       And I add a shell build step "sleep 5"
       And I save the job
       And I build the job
       Then the configurations should run sequentially
     */
    @Test
    public void run_configurations_sequentially() {
        job.configure();
        job.addUserAxis("user_axis","axis1 axis2 axis3");
        job.runSequentially.check();
        job.addShellStep("sleep 5");
        job.save();

        MatrixBuild b = job.queueBuild().waitUntilStarted().as(MatrixBuild.class);
        assertThatBuildHasRunSequentially(b);
    }

    /**
     Scenario: Run a matrix job
       Given a matrix job
       When I configure the job
       And I configure user axis "user_axis" with values "axis1 axis2 axis3"
       And I add a shell build step "ls"
       And I save the job
       And I build the job
       Then I console output of configurations should match "+ ls"
     */
    @Test
    public void run_a_matrix_job() {
        job.configure();
        job.addUserAxis("user_axis","axis1 axis2 axis3");
        job.addShellStep("ls");
        job.save();
        job.queueBuild().shouldSucceed();
        for (MatrixConfiguration c : job.getConfigurations()) {
            c.getLastBuild().shouldContainsConsoleOutput("\\+ ls");
        }
    }

    /**
     Scenario: Run touchstone builds first with resul stable
       Given a matrix job
       When I configure the job
       And I configure user axis "user_axis" with values "axis1 axis2 axis3"
       And I add always fail build step
       And I configure to execute touchstone builds first with filter "user_axis=='axis3'" and required result "UNSTABLE"
       And I save the job
       And I build the job
       Then combination "user_axis=axis2" should not be built
       And combination "user_axis=axis1" should not be built
       And combination "user_axis=axis3" should be built
     */
    @Test
    public void run_touchstone_builds_first_with_result_stable() {
        job.configure();
        job.addUserAxis("user_axis","axis1 axis2 axis3");
        job.addShellStep("false");
        job.setTouchStoneBuild("user_axis=='axis3'","UNSTABLE");
        job.save();
        MatrixBuild b = job.queueBuild().waitUntilFinished().as(MatrixBuild.class);

        b.getConfiguration("user_axis=axis1").shouldNotExist();
        b.getConfiguration("user_axis=axis2").shouldNotExist();
        b.getConfiguration("user_axis=axis3").shouldExist();
    }

    /**
     Scenario: Run build with combination filter
       Given a matrix job
       When I configure the job
       And I configure user axis "user_axis" with values "axis1 axis2 axis3"
       And I set combination filter to "user_axis=='axis2'"
       And I add a shell build step "echo hello"
       And I save the job
       And I build the job
       Then combination "user_axis=axis2" should be built
       And combination "user_axis=axis1" should not be built
       And combination "user_axis=axis3" should not be built
     */
    @Test
    public void run_build_with_combination_filter() {
        job.configure();
        job.addUserAxis("user_axis", "axis1 axis2 axis3");
        job.setCombinationFilter("user_axis=='axis2'");
        job.addShellStep("echo hello");
        job.save();

        MatrixBuild b = job.queueBuild().waitUntilFinished().as(MatrixBuild.class);

        b.getConfiguration("user_axis=axis1").shouldNotExist();
        b.getConfiguration("user_axis=axis2").shouldExist();
        b.getConfiguration("user_axis=axis3").shouldNotExist();
    }

    /**
     @since(1.515)
     @bug(7285)
     Scenario: Use Job parameters in combination filters
       Given a matrix job
       When I configure the job
       And I configure user axis "run" with values "yes maybe no"
       And I set combination filter to "run=='yes' || (run=='maybe' && condition=='true')"
       And I add a string parameter "condition"
       And I save the job
       And I build the job with parameter
           | condition | false |
       And I build the job with parameter
           | condition | true |
       Then combination "run=yes" should be built in build 1
       Then combination "run=yes" should be built in build 2
       Then combination "run=maybe" should not be built in build 1
       Then combination "run=maybe" should be built in build 2
       Then combination "run=no" should not be built in build 1
       Then combination "run=no" should not be built in build 2
     */
    @Test @Bug("JENKINS-7285") @Since("1.515")
    public void use_job_parameters_in_combination_filters() {
        job.configure();
        job.addUserAxis("run", "yes maybe no");
        job.setCombinationFilter("run=='yes' || (run=='maybe' && condition=='true')");
        job.addParameter(StringParameter.class).setName("condition");
        job.save();

        MatrixBuild b = job.queueBuild(singletonMap("condition", "false")).as(MatrixBuild.class);
        b.getConfiguration("run=yes").shouldExist();
        b.getConfiguration("run=maybe").shouldNotExist();
        b.getConfiguration("run=no").shouldNotExist();

        b = job.queueBuild(singletonMap("condition", "true")).as(MatrixBuild.class);
        b.getConfiguration("run=yes").shouldExist();
        b.getConfiguration("run=maybe").shouldExist();
        b.getConfiguration("run=no").shouldNotExist();

    }

    private void assertThatBuildHasRunSequentially(MatrixBuild b) {
        List<MatrixRun> builds = b.getConfigurations();

        while (b.isInProgress()) {
            int running = 0;
            for (MatrixRun r : builds) {
                if (r.isInProgress())
                    running++;
            }

            assertTrue("Too many configurations running at once", running<2);
            sleep(100);
        }
    }
}