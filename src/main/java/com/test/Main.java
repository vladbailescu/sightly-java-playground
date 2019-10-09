package com.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.sling.scripting.sightly.compiler.CompilationResult;
import org.apache.sling.scripting.sightly.compiler.CompilationUnit;
import org.apache.sling.scripting.sightly.compiler.SightlyCompiler;
import org.apache.sling.scripting.sightly.extension.RuntimeExtension;
import org.apache.sling.scripting.sightly.impl.engine.ExtensionRegistryService;
import org.apache.sling.scripting.sightly.impl.engine.runtime.RenderContextImpl;
import org.apache.sling.scripting.sightly.java.compiler.ClassInfo;
import org.apache.sling.scripting.sightly.java.compiler.JavaClassBackendCompiler;
import org.apache.sling.scripting.sightly.render.RenderContext;
import org.apache.sling.scripting.sightly.render.RenderUnit;


public class Main {

    private static final String FOLDER = "src/main/resources/";
    private static final String HTL_SCRIPT = FOLDER + "test.html";
    private static final String JAVA_FILE = FOLDER + "test_html.java";
    private static final String JAVA_CLASS = FOLDER + "test_html.class";

    public static void main(String[] args)
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        String javaCode = generateJavaCode(HTL_SCRIPT);
        //System.out.println(javaCode);
        try (Writer writer = new FileWriter(JAVA_FILE)) {
            writer.write(javaCode);
        }
        generateBytecode(JAVA_FILE);
        String output = loadAndRender(JAVA_CLASS.replace(".class", "").replace("/", "."));
        System.out.println(output);
    }

    private static String generateJavaCode(String scriptPath) {
        CompilationUnit compilationUnit = readScript(scriptPath);
        ClassInfo classInfo = buildClassInfo(compilationUnit);

        JavaClassBackendCompiler backendCompiler = new JavaClassBackendCompiler();
        SightlyCompiler sightlyCompiler = new SightlyCompiler();

        CompilationResult compilationResult = sightlyCompiler.compile(compilationUnit, backendCompiler);
        String source = backendCompiler.build(classInfo);
        return source;
    }

    private static void generateBytecode(String javaFile) {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaFile);
        List<String> optionList = new ArrayList<>();
        optionList.add("-classpath");
        StringBuilder classPath = new StringBuilder(System.getProperty("java.class.path"));
        if (URLClassLoader.class.isAssignableFrom(Main.class.getClassLoader().getClass())) {
            URLClassLoader urlClassLoader = (URLClassLoader)Main.class.getClassLoader();
            for (URL url: urlClassLoader.getURLs()) {
                classPath.append(":");
                classPath.append(url.getPath());
            }
        }
        //System.out.println(classPath);
        optionList.add(classPath.toString());
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, optionList, null, compilationUnits);
        if (!task.call()) {
            diagnostics.getDiagnostics().forEach(diagnostic -> {
                System.err.println(String.format("%s [%d:%d] - %s",
                        diagnostic.getSource().getName(),
                        diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                        diagnostic.getMessage(Locale.ENGLISH)));
            });
        }
    }

    private static String loadAndRender(String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, MalformedURLException {
        URL url = new File(".").toURI().toURL();
        ClassLoader classLoader = URLClassLoader.newInstance(new URL[] { url }, Main.class.getClassLoader());
        Class loadedClass = classLoader.loadClass(className);
        StringWriter out = new StringWriter();
        Bindings arguments = new SimpleBindings();

        ((RenderUnit)loadedClass.newInstance()).render(new PrintWriter(out), getRenderContext(), arguments);
        return out.toString();
    }

    private static CompilationUnit readScript(final String scriptPath) {
        return new CompilationUnit() {
            public String getScriptName() {
                return new File(scriptPath).getPath();
            }

            public Reader getScriptReader() {
                try {
                    return new FileReader(scriptPath);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
    }

    private static ClassInfo buildClassInfo(final CompilationUnit compilationUnit) {
        final String className, packageName;
        int lastSeparator = compilationUnit.getScriptName().lastIndexOf(File.separator);
        if (lastSeparator > 0) {
            className = compilationUnit.getScriptName().substring(lastSeparator + 1).replace(".", "_");
            packageName = compilationUnit.getScriptName().substring(0, lastSeparator).replace(File.separator, ".");
        } else {
            className = compilationUnit.getScriptName().replace(".", "_");
            packageName = new File(compilationUnit.getScriptName()).getPath();
        }
        return new ClassInfo() {
            public String getSimpleClassName() {
                return className;
            }

            public String getPackageName() {
                return packageName;
            }

            public String getFullyQualifiedClassName() {
                return packageName + "." + className;
            }
        };
    }

    private static RenderContext getRenderContext() {
        Bindings bindings = new SimpleBindings();
        bindings.put("test", "TEST");

        ExtensionRegistryService extensionRegistryService = new ExtensionRegistryService();
        extensionRegistryService.extensions().putIfAbsent("xss", new RuntimeExtension() {
            @Override
            public Object call(RenderContext renderContext, Object... objects) {
                return objects[0];
            }
        });

        return new RenderContextImpl(extensionRegistryService, new ScriptContext() {
            @Override
            public void setBindings(Bindings bindings, int scope) {

            }

            @Override
            public Bindings getBindings(int scope) {
                return bindings;
            }

            @Override
            public void setAttribute(String name, Object value, int scope) {

            }

            @Override
            public Object getAttribute(String name, int scope) {
                return null;
            }

            @Override
            public Object removeAttribute(String name, int scope) {
                return null;
            }

            @Override
            public Object getAttribute(String name) {
                return null;
            }

            @Override
            public int getAttributesScope(String name) {
                return 0;
            }

            @Override
            public Writer getWriter() {
                return null;
            }

            @Override
            public Writer getErrorWriter() {
                return null;
            }

            @Override
            public void setWriter(Writer writer) {

            }

            @Override
            public void setErrorWriter(Writer writer) {

            }

            @Override
            public Reader getReader() {
                return null;
            }

            @Override
            public void setReader(Reader reader) {

            }

            @Override
            public List<Integer> getScopes() {
                return null;
            }
        });
    }

}
