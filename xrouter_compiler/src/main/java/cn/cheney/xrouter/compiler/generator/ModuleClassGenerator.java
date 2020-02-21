package cn.cheney.xrouter.compiler.generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import cn.cheney.xrouter.annotation.XMethod;
import cn.cheney.xrouter.annotation.XParam;
import cn.cheney.xrouter.compiler.XRouterProcessor;
import cn.cheney.xrouter.compiler.contant.TypeMirrorConstant;
import cn.cheney.xrouter.compiler.util.Logger;
import cn.cheney.xrouter.core.constant.GenerateFileConstant;
import cn.cheney.xrouter.core.constant.RouteType;
import cn.cheney.xrouter.core.invok.ActivityInvoke;
import cn.cheney.xrouter.core.invok.Invokable;
import cn.cheney.xrouter.core.invok.MethodInvokable;
import cn.cheney.xrouter.core.module.BaseModule;

public class ModuleClassGenerator {

    private String generatorClassName;

    private String module;


    public ModuleClassGenerator(String module) {
        this.module = module;
        this.generatorClassName = GenerateFileConstant.MODULE_FILE_PREFIX + module;
    }

    /**
     * 最后生产java文件
     *
     * @param filer out put
     */
    public void generateJavaFile(Filer filer) {
        TypeSpec typeSpec = TypeSpec.classBuilder(generatorClassName)
                .superclass(BaseModule.class)
                .addJavadoc(GenerateFileConstant.WARNING_TIPS)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(loadMethodBuilder.build())
                .addMethod(getNameMethodBuilder.addStatement("return $S", module)
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder("cn.cheney.xrouter", typeSpec)
                .build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * public void load(Map<String, MethodInvokable> routeMap) {}
     */
    private MethodSpec.Builder loadMethodBuilder = MethodSpec.methodBuilder("load")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addAnnotation(Override.class)
            .addParameter(ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Invokable.class)),
                    "routeMap");

    /**
     * public String getName() {return "home";}
     */
    private MethodSpec.Builder getNameMethodBuilder = MethodSpec.methodBuilder("getName")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addAnnotation(Override.class);


    /**
     * routeMap.put("/test2",new MethodInvoke() or new ActivityInvoke());
     */
    public void generateSeg(XRouterProcessor.Holder holder, TypeElement classType, String path) {
        TypeMirror typeActivity = holder.elementUtils
                .getTypeElement(TypeMirrorConstant.ACTIVITY).asType();

        TypeMirror typeMethod = holder.elementUtils
                .getTypeElement(TypeMirrorConstant.IMETHOD).asType();

        TypeMirror typeContext = holder.elementUtils
                .getTypeElement(TypeMirrorConstant.CONTEXT).asType();

        if (holder.types.isSubtype(classType.asType(), typeActivity)) {
            String segBuilder = "$L.put($S,new $T(" + "$T.ACTIVITY,$T.class,$S,$S" + "))";
            loadMethodBuilder.addStatement(segBuilder, "routeMap", path,
                    ActivityInvoke.class, RouteType.class, classType, module, path);
        } else if (holder.types.isSubtype(classType.asType(), typeMethod)) {
            for (Element element : classType.getEnclosedElements()) {
                if (element instanceof ExecutableElement) {
                    ExecutableElement executableElement = (ExecutableElement) element;
                    addMethodInvoke(classType, executableElement, ParameterizedTypeName.get(typeContext));
                }
            }
        }
    }


    private void addMethodInvoke(TypeElement classType, ExecutableElement methodElement, TypeName contextName) {
        XMethod xMethod = methodElement.getAnnotation(XMethod.class);
        if (null == xMethod) {
            return;
        }
        Set<Modifier> modifiers = methodElement.getModifiers();
        if (null == modifiers) {
            Logger.e(String.format("[%s] [%s]  Must public static !!",
                    classType.getQualifiedName(),
                    methodElement.getSimpleName()));
            return;
        }
        if (!modifiers.contains(Modifier.PUBLIC) || !modifiers.contains(Modifier.STATIC)) {
            Logger.e(String.format("[%s] [%s]  Must public static !!",
                    classType.getQualifiedName(),
                    methodElement.getSimpleName()));
            return;
        }
        TypeMirror typeMirror = methodElement.getReturnType();
        boolean isReturnVoid = TypeKind.VOID.equals(typeMirror.getKind());

        List<? extends VariableElement> parameters = methodElement.getParameters();
        List<Object> paramsSegList = new ArrayList<>();
        paramsSegList.add(classType);
        paramsSegList.add(methodElement.getSimpleName().toString());

        StringBuilder paramSeg = new StringBuilder();
        if (isReturnVoid) {
            paramSeg.append("$T.$L(");
        } else {
            paramSeg.append("return $T.$L(");
        }
        if (null != parameters && !parameters.isEmpty()) {
            for (VariableElement variableElement : parameters) {
                TypeMirror methodParamType = variableElement.asType();
                String key;
                XParam xParam = variableElement.getAnnotation(XParam.class);
                if (null != xParam && !xParam.name().isEmpty()) {
                    key = xParam.name();
                } else {
                    key = variableElement.getSimpleName().toString();
                }

                if (parameters.indexOf(variableElement) == parameters.size() - 1) {
                    paramSeg.append("($T)params.get($S))");
                } else {
                    paramSeg.append("($T)params.get($S),");
                }
                paramsSegList.add(methodParamType);
                paramsSegList.add(key);
            }

        }
        /*
         *   new MethodInvokable(RouteType.METHOD,YourClass.class,module,path) {
         *       @Override
         *       public Objstect invoke(Context context, Map<String, Object> params) {
         *         return YourClass.YourMethod(params.get(yourKey));
         *       }
         *     }
         */
        MethodSpec.Builder invokeBuilder = MethodSpec.methodBuilder("invoke")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(contextName, "context")
                .addParameter(ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ClassName.get(Object.class)), "params")
                .addStatement(paramSeg.toString(), paramsSegList.toArray())
                .returns(Object.class);

        if (isReturnVoid) {
            invokeBuilder.addStatement("return $T.TYPE", Void.class);
        }

        String methodInvokeClassStr = "$T.METHOD,$T.class,$S,$S";
        TypeSpec methodInvoke = TypeSpec.anonymousClassBuilder(methodInvokeClassStr,
                RouteType.class, classType, module, xMethod.name())
                .addSuperinterface(MethodInvokable.class)
                .addMethod(invokeBuilder.build())
                .build();
        loadMethodBuilder.addStatement("$L.put($S,$L)", "routeMap", xMethod.name(), methodInvoke);
    }

}