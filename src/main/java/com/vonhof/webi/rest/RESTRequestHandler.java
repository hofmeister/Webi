package com.vonhof.webi.rest;

import com.thoughtworks.paranamer.AdaptiveParanamer;
import com.thoughtworks.paranamer.Paranamer;
import com.vonhof.babelshark.*;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.webi.HttpException;
import com.vonhof.webi.RequestHandler;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.WebiContext.GETMap;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.rest.DefaultUrlMapper;
import com.vonhof.webi.rest.UrlMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import javax.servlet.ServletException;

/**
 * REST web service request handling
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class RESTRequestHandler implements RequestHandler {
    
    private final Paranamer paranamer = new AdaptiveParanamer();
    private final UrlMapper urlMapper;
    private final BabelShark bs = BabelShark.getInstance();

    public RESTRequestHandler(UrlMapper urlMapper) {
        this.urlMapper = urlMapper;
    }
    
    public RESTRequestHandler() {
        this(new DefaultUrlMapper());
    }
    public void expose(Object obj) {
        urlMapper.expose(obj);
    };
    public void expose(Object obj, String baseUrl) {
        urlMapper.expose(obj, baseUrl);
    }
    
    public void handle(WebiContext ctxt) throws IOException, ServletException {
        try {
            
            setResponseType(ctxt);
            
            //Invoke REST method
            Object output = invokeAction(ctxt);
            
            ctxt.setHeader("Content-type", ctxt.getResponseType());
            final Output out = new Output(ctxt.getOutputStream(),ctxt.getResponseType());
            try {
                bs.write(out,output);
            } catch (MappingException ex) {
                throw new IOException(ex);
            }
            ctxt.flushBuffer();
        } catch (Throwable ex) {
            ctxt.sendError(ex);
        }
    }

    private Object invokeAction(WebiContext req) throws HttpException {
        String path = req.getPath();
        if (!path.isEmpty())
            path = path.substring(1);
        try {
            Object obj = urlMapper.getObjectByURL(path);
            if (obj == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }
            Method method = urlMapper.getMethodByURL(path, req.getMethod());
            if (method == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            final List<Parameter> methodParms = fromMethod(method);
            final Object[] callParms = mapRequestToMethod(req,methodParms);
            
            Object output = method.invoke(obj, callParms);
            return refineValue(output,method.getReturnType());
        } catch (HttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HttpException(HttpException.INTERNAL_ERROR, ex);
        }
    }
    
    private void setResponseType(WebiContext ctxt) {
        String format = ctxt.GET().get("format");
        if (format == null) {
            format = bs.getDefaultType();
        }
        //Set response type
        ctxt.setResponseType(bs.getMimeType(format));
    }

    private Object[] mapRequestToMethod(WebiContext req,final List<Parameter> methodParms) throws Exception {
        final GETMap GET = req.GET();
        final Object[] invokeArgs = new Object[methodParms.size()];
        
        if (!methodParms.isEmpty()) {

            parmLoop:
            for (int i = 0; i < methodParms.size(); i++) {
                Object value = null;
                Parameter p = methodParms.get(i);
                if (p.ignore()) {
                    continue;
                }
                
                String name = p.getName();

                switch (p.getParmType()) {
                    case PATH:
                        break;
                    case HEADER:
                        String headerValue = req.getHeader(name);
                        if (ReflectUtils.isPrimitive(p.getType())) {
                            value = ConvertUtils.convert(headerValue, p.getType());
                        } else {
                            value = headerValue;
                        }
                        break;
                    default:
                        if (p.hasAnnotation(Body.class)) {
                            value = readBODYParm(req,p);
                            break;
                        }
                        if (InputStream.class.isAssignableFrom(p.getType())) {
                            value = req.getInputStream();
                            break;
                        }
                        if (OutputStream.class.isAssignableFrom(p.getType())) {
                            value = req.getOutputStream();
                            break;
                        }
                        if (WebiContext.class.isAssignableFrom(p.getType())) {
                            value = req;
                            break;
                        }
                        
                        String[] values = GET.getAll(name);
                        if (values == null) {
                            values = p.getDefaultValue();
                        }

                        value = readGETParm(p, values);
                        break;
                }
                
                value = refineValue(value,p.getType());
                
                if (p.isRequired() && isMissing(value))
                    throw new HttpException(HttpException.CLIENT,"Bad request - missing required parameter: "+name);
                
                invokeArgs[i] = value;
            }
        }
        return invokeArgs;
    }
    
    private Object refineValue(Object value,Class type) {
        if (value != null) 
            return value;
        //Make sure certain values never is null
        if (type.equals(String.class))
            return "";
        if (type.isArray())
            return new Object[0];
        if (Set.class.isAssignableFrom(type))
            return Collections.EMPTY_SET;
        if (Map.class.isAssignableFrom(type))
            return Collections.EMPTY_MAP;
        if (List.class.isAssignableFrom(type))
            return Collections.EMPTY_LIST;
        return value;
    }
    
    private boolean isMissing(Object value) {
        boolean missing = value == null;
        if (!missing && value instanceof String) {
            missing = ((String)value).isEmpty();
        }

        if (!missing && value instanceof Date) {
            missing = ((Date)value).getTime() == 0;
        }

        if (!missing && value instanceof Collection) {
            missing = ((Collection)value).isEmpty();
        }

        if (!missing && value instanceof Map) {
            missing = ((Map)value).isEmpty();
        }
        return missing;
    }
    
    private Object readBODYParm(WebiContext req,Parameter p) throws Exception {
        return bs.read(new Input(req.getInputStream(), req.getRequestType()), p.getType());
    }

    private Object readGETParm(Parameter p, String[] values) throws Exception {
        if (values != null && values.length > 0) {
            if (ReflectUtils.isCollection(p.getType())) {
                if (p.getType().
                        isArray()) {
                    Object[] realValues = new Object[values.length];
                    for (int x = 0; x < values.length; x++) {
                        realValues[x] = ConvertUtils.convert(values[x], p.getType());
                    }
                    return realValues;
                } else {
                    Collection list = (Collection) p.getType().newInstance();
                    list.addAll(Arrays.asList(values));
                    return list;
                }

            } else if (ReflectUtils.isPrimitive(p.getType())) {
                return ConvertUtils.convert(values[0], p.getType());
            }
            return values[0];
        }
        return null;
    }

    private List<Parameter> fromMethod(Method m) {
        String[] parmNames = paranamer.lookupParameterNames(m, false);

        Class<?>[] parmTypes = m.getParameterTypes();
        Annotation[][] parmAnnotations = m.getParameterAnnotations();

        List<Parameter> out = new LinkedList<Parameter>();
        for (int i = 0; i < parmTypes.length; i++) {
            out.add(new Parameter(parmNames[i], parmTypes[i], parmAnnotations[i]));
        }

        return out;
    }

    private static final class Parameter {

        private final String name;
        private final Class type;
        private final Map<Class<? extends Annotation>, Annotation> annotations =
                new HashMap<Class<? extends Annotation>, Annotation>();
        private final Parm parmAnno;

        public Parameter(String name, Class type, Annotation[] annotations) {
            this.type = type;
            for (Annotation a : annotations) {
                this.annotations.put(a.annotationType(), a);
            }

            parmAnno = getAnnotation(Parm.class);
            if (parmAnno != null
                    && !parmAnno.value().
                    isEmpty()) {
                name = parmAnno.value();
            }
            this.name = name;

        }

        public boolean ignore() {
            return hasAnnotation(Ignore.class);
        }

        public Parm.Type getParmType() {
            return parmAnno != null ? parmAnno.type() : Parm.Type.AUTO;
        }

        public String[] getDefaultValue() {
            return parmAnno != null ? parmAnno.defaultValue() : new String[0];
        }

        public <T extends Annotation> T getAnnotation(Class<T> type) {
            return (T) this.annotations.get(type);
        }

        public <T extends Annotation> boolean hasAnnotation(Class<T> type) {
            return this.annotations.containsKey(type);
        }

        public String getName() {
            return name;
        }

        public Class getType() {
            return type;
        }

        private boolean isRequired() {
            return parmAnno != null ? parmAnno.required() : false;
        }
    }

}