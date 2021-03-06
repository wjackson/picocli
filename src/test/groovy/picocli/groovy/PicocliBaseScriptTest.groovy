/*
 * Copyright 2017 the original author or authors.
 *
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
 */

package picocli.groovy

import groovy.transform.SourceURI
import org.junit.Ignore
import org.junit.Test
import picocli.CommandLine
import picocli.CommandLine.ExecutionException

import java.nio.charset.Charset

import static org.junit.Assert.*

/**
 * @author Jim White
 * @author Remko Popma
 * @since 2.0
 */
public class PicocliBaseScriptTest {
    @SourceURI URI sourceURI

    @Test
    void testParameterizedScript() {
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args',
                ["--codepath", "/usr/x.jar", "-cp", "/bin/y.jar", "-cp", "z", "--", "placeholder", "another"] as String[])
        def result = shell.evaluate '''
@groovy.transform.BaseScript(picocli.groovy.PicocliBaseScript)
import groovy.transform.Field
import picocli.CommandLine

@CommandLine.Parameters
@Field List<String> parameters

@CommandLine.Option(names = ["-cp", "--codepath"])
@Field List<String> codepath = []

//println parameters
//println codepath

assert parameters == ['placeholder', 'another']
assert codepath == ['/usr/x.jar', '/bin/y.jar', 'z']

[parameters.size(), codepath.size()]
'''
        assert result == [2, 3]
    }

    @Test
    void testSimpleCommandScript() {
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args',
                [ "--codepath", "/usr/x.jar", "placeholder", "-cp", "/bin/y.jar", "another" ] as String[])
        def result = shell.evaluate(new File(new File(sourceURI).parentFile, 'SimpleCommandScriptTest.groovy'))
        assert result == [777]
    }

    @Test
    void testRunnableSubcommand() {
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args',
                [ "-verbose=2", "commit", "--amend", "--author=Remko", "MultipleCommandScriptTest.groovy"] as String[])
        def result = shell.evaluate(new File(new File(sourceURI).parentFile, 'MultipleCommandScriptTest.groovy'))
        assert result == ["MultipleCommandScriptTest.groovy"]
    }

    @Test
    void testCallableSubcommand() {
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args',
                [ "-verbose=2", "add", "-i", "zoos" ] as String[])
        def result = shell.evaluate(new File(new File(sourceURI).parentFile, 'MultipleCommandScriptTest.groovy'))
        assert result == ["zoos"]
    }

    @Test
    void testScriptAutomaticUsageHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args', ["--help"] as String[])
        shell.evaluate '''
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

@CommandLine.Option(names = ["-h", "--help"], usageHelp = true)
@Field boolean usageHelpRequested
'''
        String expected = String.format("" +
                "Usage: Script1 [-h]%n" +
                "  -h, --help%n")
        assert expected == new String(baos.toByteArray(), Charset.defaultCharset())
    }

    @Test
    void testScriptAutomaticVersionHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args', ["--version"] as String[])
        shell.evaluate '''
@picocli.CommandLine.Command(version = "best version ever v1.2.3")
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

@CommandLine.Option(names = ["-V", "--version"], versionHelp = true)
@Field boolean usageHelpRequested
'''
        String expected = String.format("" +
                "best version ever v1.2.3%n")
        assert expected == new String(baos.toByteArray(), Charset.defaultCharset())
    }

    @Test
    void testScriptExecutionExceptionWrapsException() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        String script = '''
@picocli.CommandLine.Command
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

throw new IllegalStateException("Hi this is a test exception")
'''
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args', [] as String[])
        try {
            shell.evaluate script
            fail("Expected exception")
        } catch (ExecutionException ex) {
            assert "java.lang.IllegalStateException: Hi this is a test exception" == ex.getMessage()
            assert ex.getCause() instanceof IllegalStateException
        }
    }

    @Test
    void testScriptExecutionException() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        String script = '''
@picocli.CommandLine.Command
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

throw new CommandLine.ExecutionException(new CommandLine(this), "Hi this is a test ExecutionException")
'''
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args', [] as String[])
        try {
            shell.evaluate script
            fail("Expected exception")
        } catch (ExecutionException ex) {
            assert "Hi this is a test ExecutionException" == ex.getMessage()
            assert ex.getCause() == null
        }
    }

    @Test
    void testScriptCallsHandleExecutionException() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        String script = '''
@picocli.CommandLine.Command
@picocli.groovy.PicocliScript
import picocli.CommandLine

public Object handleExecutionException(CommandLine commandLine, String[] args, Exception ex) {
    return ex
}
    
throw new CommandLine.ExecutionException(new CommandLine(this), "Hi this is a test handleExecutionException")
'''
        GroovyShell shell = new GroovyShell()
        shell.context.setVariable('args', [] as String[])
        def result = shell.evaluate script
        assert result instanceof ExecutionException
        assert "Hi this is a test handleExecutionException" == result.getMessage()
        assert result.getCause() == null
    }

    @Test
    void testScriptBindingNullCommandLine() {

        Binding binding = new Binding()
        binding.setProperty("commandLine", null)
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        String script = '''
@picocli.CommandLine.Command
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

throw new IllegalStateException("Hi this is a test exception")
'''
        GroovyShell shell = new GroovyShell(binding)
        shell.context.setVariable('args', [] as String[])
        try {
            shell.evaluate script
            fail("Expected exception")
        } catch (ExecutionException ex) {
            assert "java.lang.IllegalStateException: Hi this is a test exception" == ex.getMessage()
        }
    }

    private class Params {
        @CommandLine.Parameters String[] positional
        @CommandLine.Option(names = "-o") option
    }

    @Test
    void testScriptBindingCommandLine() {

        CommandLine commandLine = new CommandLine(new Params())
        Binding binding = new Binding()
        binding.setProperty("commandLine", commandLine)
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        String script = '''
@picocli.CommandLine.Command
@picocli.groovy.PicocliScript
import groovy.transform.Field
import picocli.CommandLine

throw new IllegalStateException("Hi this is a test exception")
'''
        GroovyShell shell = new GroovyShell(binding)
        shell.context.setVariable('args', ["-o=hi", "123"] as String[])
        try {
            shell.evaluate script
            fail("Expected exception")
        } catch (ExecutionException ex) {
            assert "java.lang.IllegalStateException: Hi this is a test exception" == ex.getMessage()
        }
        Params params = commandLine.command
        assert params.option == "hi"
        assert params.positional.contains("123")
    }

}
