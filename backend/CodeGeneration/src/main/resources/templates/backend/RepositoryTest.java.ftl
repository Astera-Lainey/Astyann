package ${project.packageName}.repository;

import ${project.packageName}.entity.${entity.className};
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice test for {@link ${entity.className}}. Exercises the mapping against a real (H2)
 * database, so it catches column/type mismatches, bad constraints and broken relationships that
 * compilation cannot see.
 */
@DataJpaTest
@ActiveProfiles("test")
class ${entity.className}RepositoryTest {

    @Autowired
    private ${entity.className}Repository repository;

    @Test
    void repositoryIsWired() {
        assertThat(repository).isNotNull();
        assertThat(repository.findAll()).isNotNull();
    }
<#if entity.testable>

    @Test
    void savesAndReadsBack() {
        ${entity.className} ${entity.instanceName} = new ${entity.className}();
<#list entity.fields as field>
<#if field.sampleValue??>
        ${entity.instanceName}.set${field.name?cap_first}(${field.sampleValue});
</#if>
</#list>

        ${entity.className} saved = repository.save(${entity.instanceName});

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }
</#if>
}
