package com.mycompany.bugtracker.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import javax.annotation.Nonnull;

public class ReactivePageableHandlerMethodArgumentResolver implements SyncHandlerMethodArgumentResolver {

    private static final String INVALID_DEFAULT_PAGE_SIZE = "Invalid default page size configured for method %s! Must not be less than one!";

    private static final String DEFAULT_PAGE_PARAMETER = "page";
    private static final String DEFAULT_SIZE_PARAMETER = "size";
    private static final String DEFAULT_PREFIX = "";
    private static final String DEFAULT_QUALIFIER_DELIMITER = "_";
    private static final int DEFAULT_MAX_PAGE_SIZE = 2000;
    private static final ReactiveSortHandlerMethodArgumentResolver DEFAULT_SORT_RESOLVER = new ReactiveSortHandlerMethodArgumentResolver();
    static final Pageable DEFAULT_PAGE_REQUEST = PageRequest.of(0, 20);

    private Pageable fallbackPageable = DEFAULT_PAGE_REQUEST;
    private String pageParameterName = DEFAULT_PAGE_PARAMETER;
    private String sizeParameterName = DEFAULT_SIZE_PARAMETER;
    private String prefix = DEFAULT_PREFIX;
    private String qualifierDelimiter = DEFAULT_QUALIFIER_DELIMITER;
    private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private boolean oneIndexedParameters = false;
    private ReactiveSortHandlerMethodArgumentResolver sortResolver;

    /**
     * Constructs an instance of this resolved with a default {@link ReactiveSortHandlerMethodArgumentResolver}.
     */
    public ReactivePageableHandlerMethodArgumentResolver() {
        this(DEFAULT_SORT_RESOLVER);
    }

    public ReactivePageableHandlerMethodArgumentResolver(ReactiveSortHandlerMethodArgumentResolver sortResolver) {

        Assert.notNull(sortResolver, "ReactiveSortHandlerMethodArgumentResolver must not be null!");

        this.sortResolver = sortResolver;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver#supportsParameter(org.springframework.core.MethodParameter)
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.equals(parameter.getParameterType());
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver#resolveArgumentValue(org.springframework.core.MethodParameter, org.springframework.web.reactive.BindingContext, org.springframework.web.server.ServerWebExchange)
     */
    @Nonnull
    @Override
    public Pageable resolveArgumentValue(MethodParameter parameter, BindingContext bindingContext,
                                         ServerWebExchange exchange) {

        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        String page = queryParams.getFirst(getParameterNameToUse(getPageParameterName(), parameter));
        String pageSize = queryParams.getFirst(getParameterNameToUse(getSizeParameterName(), parameter));

        Sort sort = sortResolver.resolveArgumentValue(parameter, bindingContext, exchange);

        Pageable pageable = getPageable(parameter, page, pageSize);

        return sort.isSorted() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort) : pageable;
    }

    /**
     * Configures the {@link Pageable} to be used as fallback in case no {@link PageableDefault} or
     * {@link PageableDefault} (the latter only supported in legacy mode) can be found at the method parameter to be
     * resolved.
     * <p>
     * If you set this to {@link Optional#empty()}, be aware that you controller methods will get {@code null}
     * handed into them in case no {@link Pageable} data can be found in the request. Note, that doing so will require you
     * supply bot the page <em>and</em> the size parameter with the requests as there will be no default for any of the
     * parameters available.
     *
     * @param fallbackPageable the {@link Pageable} to be used as general fallback.
     */
    public void setFallbackPageable(Pageable fallbackPageable) {

        Assert.notNull(fallbackPageable, "Fallback Pageable must not be null!");

        this.fallbackPageable = fallbackPageable;
    }

    /**
     * Returns whether the given {@link Pageable} is the fallback one.
     *
     * @param pageable can be {@code null}.
     * @return
     */
    public boolean isFallbackPageable(Pageable pageable) {
        return fallbackPageable == null ? false : fallbackPageable.equals(pageable);
    }

    /**
     * Configures the maximum page size to be accepted. This allows to put an upper boundary of the page size to prevent
     * potential attacks trying to issue an {@link OutOfMemoryError}. Defaults to {@link #DEFAULT_MAX_PAGE_SIZE}.
     *
     * @param maxPageSize the maxPageSize to set.
     */
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    /**
     * Retrieves the maximum page size to be accepted. This allows to put an upper boundary of the page size to prevent
     * potential attacks trying to issue an {@link OutOfMemoryError}. Defaults to {@link #DEFAULT_MAX_PAGE_SIZE}.
     *
     * @return the maximum page size allowed.
     */
    protected int getMaxPageSize() {
        return this.maxPageSize;
    }

    /**
     * Configures the parameter name to be used to find the page number in the request. Defaults to {@code page}.
     *
     * @param pageParameterName the parameter name to be used, must not be {@code null} or empty.
     */
    public void setPageParameterName(String pageParameterName) {

        Assert.hasText(pageParameterName, "Page parameter name must not be null or empty!");
        this.pageParameterName = pageParameterName;
    }

    /**
     * Retrieves the parameter name to be used to find the page number in the request. Defaults to {@code page}.
     *
     * @return the parameter name to be used, never {@code null} or empty.
     */
    protected String getPageParameterName() {
        return this.pageParameterName;
    }

    /**
     * Configures the parameter name to be used to find the page size in the request. Defaults to {@code size}.
     *
     * @param sizeParameterName the parameter name to be used, must not be {@code null} or empty.
     */
    public void setSizeParameterName(String sizeParameterName) {

        Assert.hasText(sizeParameterName, "Size parameter name must not be null or empty!");
        this.sizeParameterName = sizeParameterName;
    }

    /**
     * Retrieves the parameter name to be used to find the page size in the request. Defaults to {@code size}.
     *
     * @return the parameter name to be used, never {@code null} or empty.
     */
    protected String getSizeParameterName() {
        return this.sizeParameterName;
    }

    /**
     * Configures a general prefix to be prepended to the page number and page size parameters. Useful to namespace the
     * property names used in case they are clashing with ones used by your application. By default, no prefix is used.
     *
     * @param prefix the prefix to be used or {@code null} to reset to the default.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix == null ? DEFAULT_PREFIX : prefix;
    }

    /**
     * The delimiter to be used between the qualifier and the actual page number and size properties. Defaults to
     * {@code _}. So a qualifier of {@code foo} will result in a page number parameter of {@code foo_page}.
     *
     * @param qualifierDelimiter the delimiter to be used or {@code null} to reset to the default.
     */
    public void setQualifierDelimiter(String qualifierDelimiter) {
        this.qualifierDelimiter = qualifierDelimiter == null ? DEFAULT_QUALIFIER_DELIMITER : qualifierDelimiter;
    }

    /**
     * Configures whether to expose and assume 1-based page number indexes in the request parameters. Defaults to
     * {@code false}, meaning a page number of 0 in the request equals the first page. If this is set to
     * {@code true}, a page number of 1 in the request will be considered the first page.
     *
     * @param oneIndexedParameters the oneIndexedParameters to set.
     */
    public void setOneIndexedParameters(boolean oneIndexedParameters) {
        this.oneIndexedParameters = oneIndexedParameters;
    }

    /**
     * Indicates whether to expose and assume 1-based page number indexes in the request parameters. Defaults to
     * {@code false}, meaning a page number of 0 in the request equals the first page. If this is set to
     * {@code true}, a page number of 1 in the request will be considered the first page.
     *
     * @return whether to assume 1-based page number indexes in the request parameters.
     */
    protected boolean isOneIndexedParameters() {
        return this.oneIndexedParameters;
    }

    protected Pageable getPageable(MethodParameter methodParameter, @Nullable String pageString,
                                   @Nullable String pageSizeString) {
        assertPageableUniqueness(methodParameter);

        Optional<Pageable> defaultOrFallback = getDefaultFromAnnotationOrFallback(methodParameter).toOptional();

        Optional<Integer> page = parseAndApplyBoundaries(pageString, Integer.MAX_VALUE, true);
        Optional<Integer> pageSize = parseAndApplyBoundaries(pageSizeString, maxPageSize, false);

        if (!(page.isPresent() && pageSize.isPresent()) && !defaultOrFallback.isPresent()) {
            return Pageable.unpaged();
        }

        int p = page
            .orElseGet(() -> defaultOrFallback.map(Pageable::getPageNumber).orElseThrow(IllegalStateException::new));
        int ps = pageSize
            .orElseGet(() -> defaultOrFallback.map(Pageable::getPageSize).orElseThrow(IllegalStateException::new));

        // Limit lower bound
        ps = ps < 1 ? defaultOrFallback.map(Pageable::getPageSize).orElseThrow(IllegalStateException::new) : ps;
        // Limit upper bound
        ps = ps > maxPageSize ? maxPageSize : ps;

        return PageRequest.of(p, ps, defaultOrFallback.map(Pageable::getSort).orElseGet(Sort::unsorted));
    }

    /**
     * Returns the name of the request parameter to find the {@link Pageable} information in. Inspects the given
     * {@link MethodParameter} for {@link Qualifier} present and prefixes the given source parameter name with it.
     *
     * @param source the basic parameter name.
     * @param parameter the {@link MethodParameter} potentially qualified.
     * @return the name of the request parameter.
     */
    protected String getParameterNameToUse(String source, @Nullable MethodParameter parameter) {

        StringBuilder builder = new StringBuilder(prefix);

        Qualifier qualifier = parameter == null ? null : parameter.getParameterAnnotation(Qualifier.class);

        if (qualifier != null) {
            builder.append(qualifier.value());
            builder.append(qualifierDelimiter);
        }

        return builder.append(source).toString();
    }

    private Pageable getDefaultFromAnnotationOrFallback(MethodParameter methodParameter) {

        PageableDefault defaults = methodParameter.getParameterAnnotation(PageableDefault.class);

        if (defaults != null) {
            return getDefaultPageRequestFrom(methodParameter, defaults);
        }

        return fallbackPageable;
    }

    private static Pageable getDefaultPageRequestFrom(MethodParameter parameter, PageableDefault defaults) {

        Integer defaultPageNumber = defaults.page();
        Integer defaultPageSize = getSpecificPropertyOrDefaultFromValue(defaults, "size");

        if (defaultPageSize < 1) {
            Method annotatedMethod = parameter.getMethod();
            throw new IllegalStateException(String.format(INVALID_DEFAULT_PAGE_SIZE, annotatedMethod));
        }

        if (defaults.sort().length == 0) {
            return PageRequest.of(defaultPageNumber, defaultPageSize);
        }

        return PageRequest.of(defaultPageNumber, defaultPageSize, defaults.direction(), defaults.sort());
    }

    /**
     * Tries to parse the given {@link String} into an integer and applies the given boundaries. Will return 0 if the
     * {@link String} cannot be parsed.
     *
     * @param parameter the parameter value.
     * @param upper the upper bound to be applied.
     * @param shiftIndex whether to shift the index if {@link #oneIndexedParameters} is set to true.
     * @return the parsed integer.
     */
    private Optional<Integer> parseAndApplyBoundaries(@Nullable String parameter, int upper, boolean shiftIndex) {

        if (!StringUtils.hasText(parameter)) {
            return Optional.empty();
        }

        try {
            int parsed = Integer.parseInt(parameter) - (oneIndexedParameters && shiftIndex ? 1 : 0);
            return Optional.of(parsed < 0 ? 0 : parsed > upper ? upper : parsed);
        } catch (NumberFormatException e) {
            return Optional.of(0);
        }
    }

    public static void assertPageableUniqueness(MethodParameter parameter) {

        Method method = parameter.getMethod();

        if (method == null) {
            throw new IllegalArgumentException(String.format("Method parameter %s is not backed by a method.", parameter));
        }

        if (containsMoreThanOnePageableParameter(method)) {
            Annotation[][] annotations = method.getParameterAnnotations();
            assertQualifiersFor(method.getParameterTypes(), annotations);
        }
    }

    /**
     * Returns whether the given {@link Method} has more than one {@link Pageable} parameter.
     *
     * @param method must not be {@code null}.
     * @return whether the given {@link Method} has more than one {@link Pageable} parameter.
     */
    private static boolean containsMoreThanOnePageableParameter(Method method) {

        boolean pageableFound = false;

        for (Class<?> type : method.getParameterTypes()) {

            if (pageableFound && type.equals(Pageable.class)) {
                return true;
            }

            if (type.equals(Pageable.class)) {
                pageableFound = true;
            }
        }

        return false;
    }

    /**
     * Returns the value of the given specific property of the given annotation. If the value of that property is the
     * properties default, we fall back to the value of the {@code value} attribute.
     *
     * @param annotation must not be {@code null}.
     * @param property must not be {@code null} or empty.
     * @return the value of the given specific property of the given annotation.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getSpecificPropertyOrDefaultFromValue(Annotation annotation, String property) {

        Object propertyDefaultValue = AnnotationUtils.getDefaultValue(annotation, property);
        Object propertyValue = AnnotationUtils.getValue(annotation, property);

        Object result = ObjectUtils.nullSafeEquals(propertyDefaultValue, propertyValue) //
            ? AnnotationUtils.getValue(annotation) //
            : propertyValue;

        if (result == null) {
            throw new IllegalStateException("Exepected to be able to look up an annotation property value but failed!");
        }

        return (T) result;
    }

    /**
     * Asserts that every {@link Pageable} parameter of the given parameters carries an {@link Qualifier} annotation to
     * distinguish them from each other.
     *
     * @param parameterTypes must not be {@code null}.
     * @param annotations must not be {@code null}.
     */
    public static void assertQualifiersFor(Class<?>[] parameterTypes, Annotation[][] annotations) {

        Set<String> values = new HashSet<>();

        for (int i = 0; i < annotations.length; i++) {

            if (Pageable.class.equals(parameterTypes[i])) {

                Qualifier qualifier = findAnnotation(annotations[i]);

                if (null == qualifier) {
                    throw new IllegalStateException(
                        "Ambiguous Pageable arguments in handler method. If you use multiple parameters of type Pageable you need to qualify them with @Qualifier");
                }

                if (values.contains(qualifier.value())) {
                    throw new IllegalStateException("Values of the user Qualifiers must be unique!");
                }

                values.add(qualifier.value());
            }
        }
    }

    /**
     * Returns the first {@link Qualifier} annotation from the given array of {@link Annotation}s. Returns {@code null} if the
     * array does not contain a {@link Qualifier} annotation.
     *
     * @param annotations must not be {@code null}.
     * @return the first {@link Qualifier} annotation from the given array of {@link Annotation}s.
     */
    @Nullable
    private static Qualifier findAnnotation(Annotation[] annotations) {

        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier) {
                return (Qualifier) annotation;
            }
        }

        return null;
    }
}
