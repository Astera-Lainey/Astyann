package ${project.packageName}.service;

import ${project.packageName}.dto.*;

import java.util.List;
import java.util.UUID;

public interface ${module.serviceName} {

<#list module.endpoints as ep>
    ${ep.returnType} ${ep.methodName}(<#if ep.hasPathVariable>UUID id<#if ep.hasRequestBody>, </#if></#if><#if ep.hasRequestBody>${ep.requestBodyType} request</#if>);
</#list>
}
