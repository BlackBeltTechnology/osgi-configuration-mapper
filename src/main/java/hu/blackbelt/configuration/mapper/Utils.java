package hu.blackbelt.configuration.mapper;

import lombok.SneakyThrows;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class Utils {
    private Utils() {
    }

    /**
     * Converts {@link Dictionary} to {@link Map}.
     *
     * @param dictionary input dictionary
     * @param <K> key type
     * @param <V> value type
     * @return map or null if the input was null
     */
    public static <K, V> Map<K, V> fromDictionary(Dictionary<K, V> dictionary) {
        if (dictionary == null) {
            return null;
        }
        Map<K, V> map = new HashMap<K, V>(dictionary.size());
        Enumeration<K> keys = dictionary.keys();
        while (keys.hasMoreElements()) {
            K key = keys.nextElement();
            map.put(key, dictionary.get(key));
        }
        return map;
    }

    /**
     * Converts {@link Properties} format text to {@link Map}.
     * @param propertiesText {@link Properties} format text
     * @return map or null if the input was null,
     *     the map is empty if no key-value pairs were found in the input
     */
    @SneakyThrows(IOException.class)
    public static Map<String, String> fromPropertiesText(String propertiesText) {
        if (propertiesText == null) {
            return null;
        }
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesText));
        return fromDictionary((Dictionary) properties);
    }


    /**
     * Returns the boolean value of the parameter or the
     * <code>defaultValue</code> if the parameter is <code>null</code>.
     * If the parameter is not a <code>Boolean</code> it is converted
     * by calling <code>Boolean.dictionaryToMap</code> on the string value of the
     * object.
     * @param propValue the property value or <code>null</code>
     * @param defaultValue the default boolean value
     */
    public static boolean toBoolean(Object propValue, boolean defaultValue) {
        propValue = toObject(propValue);
        if (propValue instanceof Boolean) {
            return (Boolean) propValue;
        } else if (propValue != null) {
            return Boolean.valueOf(String.valueOf(propValue));
        }
        return defaultValue;
    }
    /**
     * Returns the parameter as a string or the
     * <code>defaultValue</code> if the parameter is <code>null</code>.
     * @param propValue the property value or <code>null</code>
     * @param defaultValue the default string value
     */
    public static String toString(Object propValue, String defaultValue) {
        propValue = toObject(propValue);
        return (propValue != null) ? propValue.toString() : defaultValue;
    }
    /**
     * Returns the parameter as a long or the
     * <code>defaultValue</code> if the parameter is <code>null</code> or if
     * the parameter is not a <code>Long</code> and cannot be converted to
     * a <code>Long</code> from the parameter's string value.
     * @param propValue the property value or <code>null</code>
     * @param defaultValue the default long value
     */
    public static long toLong(Object propValue, long defaultValue) {
        propValue = toObject(propValue);
        if (propValue instanceof Long) {
            return (Long) propValue;
        } else if (propValue != null) {
            try {
                return Long.valueOf(String.valueOf(propValue));
            } catch (NumberFormatException nfe) {
                // don't care, fall through to default value
            }
        }
        return defaultValue;
    }
    /**
     * Returns the parameter as an integer or the
     * <code>defaultValue</code> if the parameter is <code>null</code> or if
     * the parameter is not an <code>Integer</code> and cannot be converted to
     * an <code>Integer</code> from the parameter's string value.
     * @param propValue the property value or <code>null</code>
     * @param defaultValue the default integer value
     */
    public static int toInteger(Object propValue, int defaultValue) {
        propValue = toObject(propValue);
        if (propValue instanceof Integer) {
            return (Integer) propValue;
        } else if (propValue != null) {
            try {
                return Integer.valueOf(String.valueOf(propValue));
            } catch (NumberFormatException nfe) {
                // don't care, fall through to default value
            }
        }
        return defaultValue;
    }
    /**
     * Returns the parameter as a double or the
     * <code>defaultValue</code> if the parameter is <code>null</code> or if
     * the parameter is not a <code>Double</code> and cannot be converted to
     * a <code>Double</code> from the parameter's string value.
     * @param propValue the property value or <code>null</code>
     * @param defaultValue the default double value
     */
    public static double toDouble(Object propValue, double defaultValue) {
        propValue = toObject(propValue);
        if (propValue instanceof Double) {
            return (Double) propValue;
        } else if (propValue != null) {
            try {
                return Double.valueOf(String.valueOf(propValue));
            } catch (NumberFormatException nfe) {
                // don't care, fall through to default value
            }
        }
        return defaultValue;
    }
    /**
     * Returns the parameter as a single value. If the
     * parameter is neither an array nor a <code>java.util.Collection</code> the
     * parameter is returned unmodified. If the parameter is a non-empty array,
     * the first array element is returned. If the property is a non-empty
     * <code>java.util.Collection</code>, the first collection element is returned.
     * Otherwise <code>null</code> is returned.
     * @param propValue the parameter to convert.
     */
    public static Object toObject(Object propValue) {
        if (propValue == null) {
            return null;
        } else if (propValue.getClass().isArray()) {
            Object[] prop = (Object[]) propValue;
            return prop.length > 0 ? prop[0] : null;
        } else if (propValue instanceof Collection<?>) {
            Collection<?> prop = (Collection<?>) propValue;
            return prop.isEmpty() ? null : prop.iterator().next();
        } else {
            return propValue;
        }
    }
    /**
     * Returns the parameter as an array of Strings. If
     * the parameter is a scalar value its string value is returned as a single
     * element array. If the parameter is an array, the elements are converted to
     * String objects and returned as an array. If the parameter is a collection, the
     * collection elements are converted to String objects and returned as an array.
     * Otherwise (if the parameter is <code>null</code>) <code>null</code> is
     * returned.
     * @param propValue The object to convert.
     */
    public static String[] toStringArray(Object propValue) {
        return toStringArray(propValue, null);
    }

    /**
     * Returns the parameter as an array of Strings. If
     * the parameter is a scalar value its string value is returned as a single
     * element array. If the parameter is an array, the elements are converted to
     * String objects and returned as an array. If the parameter is a collection, the
     * collection elements are converted to String objects and returned as an array.
     * Otherwise (if the property is <code>null</code>) a provided default value is
     * returned.
     * @param propValue The object to convert.
     * @param defaultArray The default array to return.
     */
    public static String[] toStringArray(Object propValue, String[] defaultArray) {
        if (propValue == null) {
            // no value at all
            return defaultArray;
        } else if (propValue instanceof String) {
            // single string
            return new String[] { (String) propValue };
        } else if (propValue instanceof String[]) {
            // String[]
            return (String[]) propValue;
        } else if (propValue.getClass().isArray()) {
            // other array
            Object[] valueArray = (Object[]) propValue;
            List<String> values = new ArrayList<String>(valueArray.length);
            for (Object value : valueArray) {
                if (value != null) {
                    values.add(value.toString());
                }
            }
            return values.toArray(new String[values.size()]);
        } else if (propValue instanceof Collection<?>) {
            // collection
            Collection<?> valueCollection = (Collection<?>) propValue;
            List<String> valueList = new ArrayList<String>(valueCollection.size());
            for (Object value : valueCollection) {
                if (value != null) {
                    valueList.add(value.toString());
                }
            }
            return valueList.toArray(new String[valueList.size()]);
        }
        return defaultArray;
    }

    /**
     * Returns the parameter as a map with string keys and string values.
     *
     * The parameter is considered as a collection whose entries are of the form
     * key=value. The conversion has following rules
     * <ul>
     *     <li>Entries are of the form key=value</li>
     *     <li>key is trimmed</li>
     *     <li>value is trimmed. If a trimmed value results in an empty string it is treated as null</li>
     *     <li>Malformed entries like 'foo','foo=' are ignored</li>
     *     <li>Map entries maintain the input order</li>
     * </ul>
     *
     * Otherwise (if the property is <code>null</code>) a provided default value is
     * returned.
     * @param propValue The object to convert.
     * @param defaultArray The default array converted to map.
     */
    public static Map<String, String> toMap(Object propValue, String[] defaultArray) {
        String[] arrayValue = toStringArray(propValue, defaultArray);
        if (arrayValue == null) {
            return null;
        }
        //in property values
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String kv : arrayValue) {
            int indexOfEqual = kv.indexOf('=');
            if (indexOfEqual > 0) {
                String key = trimToNull(kv.substring(0, indexOfEqual));
                String value = trimToNull(kv.substring(indexOfEqual + 1));
                if (key != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /**
     * Return the concataneted version of PID and FactoryPID
     * @param pid pid name (mandatory)
     * @param factoryPid factory pid (optiopnal)
     * @return
     */
    @SuppressWarnings("checkstyle:avoidinlineconditionals")
    public static String getPidName(String pid, String factoryPid) {
        String suffix = factoryPid == null ? "" : "-" + factoryPid;
        return pid + suffix;
    }


    /**
     * Parese Pid, FactoryPid values.
     * @param name
     * @return
     */
    public static String[] parsePid(String name) {
        int n = name.indexOf('-');
        String pid = name;
        if (n > 0) {
            String factoryPid = pid.substring(n + 1);
            pid = pid.substring(0, n);
            return new String[] { pid, factoryPid };
        } else {
            return new String[] { pid, null };
        }
    }


    /**
     * Generating sha1 of the given object.
     * @param obj Any Java object
     * @return 0 if parameter null, or the SHA1 digest
     */
    @SneakyThrows(value = {IOException.class, NoSuchAlgorithmException.class})
    public static BigInteger sha1(Object obj) {

        if (obj == null) {
            return BigInteger.ZERO;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();

        MessageDigest m = MessageDigest.getInstance("SHA1");
        m.update(baos.toByteArray());

        return new BigInteger(1, m.digest());
    }

    /**
     * Loading dictionary from properties file.
     * @param is
     * @return
     * @throws IOException
     */
    public static Dictionary loadProperties(InputStream is) throws IOException {
        java.util.Properties p = new java.util.Properties();

        Dictionary ht = new Hashtable();

        InputStream in = new BufferedInputStream(is);


        try {
            in.mark(1);
            boolean isXml = in.read() == '<';
            in.reset();
            if (isXml) {
                p.loadFromXML(in);
            } else {
                // Reader reader = new InputStreamReader(in, Charsets.UTF_8);
                // Windows hack to replace backslashes to handle pathes well
                Scanner s = new Scanner(in).useDelimiter("\\A");
                String data = s.hasNext() ? s.next() : "";
                data = data.replace("\\","\\\\");
                Reader reader = new StringReader(data);
                p.load(reader);
            }
            ((Hashtable) ht).putAll(p);
        } finally {
            in.close();
        }
        return ht;

    }


    public static String readUrl(URL url) throws IOException {
        String text = "";
        URLConnection conn = url.openConnection();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }


    private static String trimToNull(String str)    {
        String ts = trim(str);
        return isEmpty(ts) ? null : ts;
    }

    private static String trim(String str){
        return str == null ? null : str.trim();
    }

    private static boolean isEmpty(String str){
        return str == null || str.length() == 0;
    }


}
