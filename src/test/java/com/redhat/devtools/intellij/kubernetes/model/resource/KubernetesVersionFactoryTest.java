/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.redhat.devtools.intellij.kubernetes.model.resource.KubernetesVersionFactory.Version;
import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesVersionFactoryTest {

    @Test
    public void create_should_create_kubernetes_version() {
        // given
        Version version = KubernetesVersionFactory.create("v42beta1");
        // when
        // then
        assertThat(version).isInstanceOf(KubernetesVersionFactory.KubernetesVersion.class);
    }

    @Test
    public void create_should_create_non_kubernetes_version() {
        // given
        Version version = KubernetesVersionFactory.create("darthVader");
        // when
        // then
        assertThat(version).isInstanceOf(KubernetesVersionFactory.NonKubernetesVersion.class);
    }

    @Test
    public void create_should_create_nonkubernetes_version_when_version_has_illegal_qualifier() {
        // given
        Version version = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("v1gamma");
        // when
        boolean isKubernetes = version.isKubernetes();
        // then
        assertThat(isKubernetes).isFalse();
    }

    @Test
    public void create_should_create_nonkubernetes_version_when_version_is_missing_major() {
        // given
        Version version = KubernetesVersionFactory.create("vbeta");
        // when
        boolean isKubernetes = version.isKubernetes();
        // then
        assertThat(isKubernetes).isFalse();
    }

    @Test
    public void isKubernetes_should_return_true_for_kubernetes_version() {
        // given
        Version version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v84");
        // when
        boolean isKubernetes = version.isKubernetes();
        // then
        assertThat(isKubernetes).isTrue();
    }

    @Test
    public void isKubernetes_should_return_false_for_non_kubernetes_version() {
        // given
        Version version = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("darthVader");
        // when
        boolean isKubernetes = version.isKubernetes();
        // then
        assertThat(isKubernetes).isFalse();
    }

    @Test
    public void FactoryCreate_should_create_kubernetes_version_when_version_is_kubernetes_version() {
        // given
        Version version = KubernetesVersionFactory.create("v10beta42");
        // when
        boolean isKubernetes = version.isKubernetes();
        // then
        assertThat(isKubernetes).isTrue();
    }

    @Test
    public void isStable_should_report_stable_when_there_is_no_qualifier() {
        // given
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v1");
        // when
        boolean stable = version.isStable();
        // then
        assertThat(stable).isTrue();
    }

    @Test
    public void isStable_should_report_unstable_when_there_is_a_qualifier() {
        // given
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha");
        // when
        boolean stable = version.isStable();
        // then
        assertThat(stable).isFalse();
    }

    @Test
    public void getMajor_should_return_major() {
        // given
        Integer majorInteger = 1;
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v" + majorInteger + "beta42");
        // when
        Integer major = version.getMajor();
        // then
        assertThat(major).isEqualTo(majorInteger);
    }

    @Test
    public void getQualifier_should_return_qualifier() {
        // given
        String beta = "beta";
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v422" + beta + "442");
        // when
        Optional<String> qualifier = version.getQualifier();
        // then
        assertThat(qualifier.get()).isEqualTo(beta);
    }

    @Test
    public void getQualifier_should_return_empty_optional_qualifier_when_there_is_no_qualifier() {
        // given
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v422");
        // when
        Optional<String> qualifier = version.getQualifier();
        // then
        assertThat(qualifier).isNotPresent();
    }

    @Test
    public void getMinor_should_return_minor() {
        // given
        Integer minorInteger = 42;
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v1alpha" + minorInteger);
        // when
        Optional<Integer> minor = version.getMinor();
        // then
        assertThat(minor.get()).isEqualTo(minorInteger);
    }

    @Test
    public void getMinor_should_return_empty_optional_when_there_is_no_minor() {
        // given
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v1alpha");
        // when
        Optional<Integer> minor = version.getMinor();
        // then
        assertThat(minor).isNotPresent();
    }

    @Test
    public void toString_kubernetesVersion_should_return_original_version_for_GA_version() {
        // given
        String versionString = "v42";
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create(versionString);
        // when
        String toString = version.toString();
        // then
        assertThat(toString).isEqualTo(versionString);
    }

    @Test
    public void toString_kubernetesVersion_should_return_original_version_for_unstable_version() {
        // given
        String versionString = "v42beta42";
        KubernetesVersionFactory.KubernetesVersion version = KubernetesVersionFactory.KubernetesVersion.FACTORY.create(versionString);
        // when
        String toString = version.toString();
        // then
        assertThat(toString).isEqualTo(versionString);
    }

    @Test
    public void compareTo_kubernetesVersion_should_be_greater_than_nonKubernetesVersion() {
        // given
        Version kVersion = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta42");
        Version nkVersion = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("lukeSkywalker");
        // when
        // then
        assertThat(kVersion).isGreaterThan(nkVersion);
    }

    @Test
    public void compareTo_nonKubernetesVersion_should_be_less_than_kubernetesVersion() {
        // given
        Version kVersion = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta42");
        Version nkVersion = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("lukeSkywalker");
        // when
        // then
        assertThat(nkVersion).isLessThan(kVersion);
    }

    @Test
    public void compareTo_same_major_version_should_be_same_as_other_major() {
        // given
        Version version42_1 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        Version version42_2 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        // when
        int majorComparison = version42_1.compareTo(version42_2);
        // then
        assertThat(majorComparison).isZero();
    }

    @Test
    public void compareTo_kubernetesVersion_with_greater_major_version_should_be_greater_than_smaller_major_version() {
        // given
        Version version42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        Version version84 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v84");
        // when
        // then
        assertThat(version84).isGreaterThan(version42);
    }

    @Test
    public void compareTo_kubernetesVersion_with_smaller_major_should_be_less_than_greater_major() {
        // given
        Version version42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        Version version84 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v84");
        // when
        // then
        assertThat(version42).isLessThan(version84);
    }

    @Test
    public void compareTo_kubernetesVersion_with_alpha_qualifier_should_be_less_than_beta_qualifier() {
        // given
        Version alpha = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha");
        Version beta = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        // when
        // then
        assertThat(alpha).isLessThan(beta);
    }

    @Test
    public void compareTo_kubernetesVersion_with_beta_qualifier_should_be_greater_than_alpha_qualifier() {
        // given
        Version alpha = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha");
        Version beta = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        // when
        // then
        assertThat(beta).isGreaterThan(alpha);
    }

    @Test
    public void compareTo_GA_kubernetesVersion_should_be_greater_than_beta_qualifier() {
        // given
        Version ga = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        Version beta = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        // when
        // then
        assertThat(ga).isGreaterThan(beta);
    }

    @Test
    public void compareTo_kubernetesVersion_same_qualifier_should_be_same_as_other_qualifier() {
        // given
        Version beta1 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        Version beta2 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        // when
        int qualifierComparison = beta1.compareTo(beta2);
        // then
        assertThat(qualifierComparison).isZero();
    }

    @Test
    public void compareTo_beta_kubernetesVersion_should_be_less_than_GA() {
        // given
        Version ga = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42");
        Version beta = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta");
        // when
        // then
        assertThat(beta).isLessThan(ga);
    }

    @Test
    public void compareTo_kubernetesVersion_greater_minor_should_be_greater_than_smaller_minor() {
        // given
        Version minor42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha42");
        Version minor84 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha84");
        // when
        // then
        assertThat(minor84).isGreaterThan(minor42);
    }

    @Test
    public void compareTo_kubernetesVersion_smaller_minor_should_be_smaller_than_greater_minor() {
        // given
        Version minor42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta42");
        Version minor84 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42beta84");
        // when
        // then
        assertThat(minor42).isLessThan(minor84);
    }

    @Test
    public void compareTo_kubernetesVersion_minor_should_be_same_as_other_minor() {
        // given
        Version minor42_1 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha42");
        Version minor42_2 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha42");
        // when
        int minorComparison = minor42_1.compareTo(minor42_2);
        // then
        assertThat(minorComparison).isZero();
    }

    @Test
    public void compareTo_kubernetesVersion_without_minor_should_be_less_than_version_with_minor() {
        // given
        Version noMinor = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha");
        Version minor42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha42");
        // when
        // then
        assertThat(noMinor).isLessThan(minor42);
    }

    @Test
    public void compareTo_kubernetesVersion_with_minor_should_be_greater_than_version_without_minor() {
        // given
        Version noMinor = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha");
        Version minor42 = KubernetesVersionFactory.KubernetesVersion.FACTORY.create("v42alpha42");
        // when
        // then
        assertThat(minor42).isGreaterThan(noMinor);
    }

    @Test
    public void compareTo_nonKubernetesVersion_should_be_same_as_other_with_same_version() {
        // given
        Version skywalker1_1 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker1");
        Version skywalker1_2 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker1");
        // when
        int minorComparison = skywalker1_1.compareTo(skywalker1_2);
        // then
        assertThat(minorComparison).isZero();
    }

    @Test
    public void compareTo_nonKubernetesVersion_with_minor_should_be_less_than_version_without_minor() {
        // given
        Version skywalker = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker");
        Version skywalker1 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker1");
        // when
        // then
        assertThat(skywalker1).isLessThan(skywalker);
    }

    @Test
    public void compareTo_nonKubernetesVersion_with_greater_minor_should_be_less_than_version_with_smaller_minor() {
        // given
        Version skywalker1 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker1");
        Version skywalker10 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker10");
        // when
        // then
        assertThat(skywalker10).isLessThan(skywalker1);
    }

    @Test
    public void compareTo_nonKubernetesVersion_with_smaller_minor_should_be_less_than_version_with_greater_minor() {
        // given
        Version skywalker1 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker1");
        Version skywalker10 = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("skywalker10");
        // when
        // then
        assertThat(skywalker1).isGreaterThan(skywalker10);
    }

    @Test
    public void compareTo_nonKubernetesVersion_alphabetically_lower_char_should_be_greater() {
        // given
        Version a = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("a");
        Version b = KubernetesVersionFactory.NonKubernetesVersion.FACTORY.create("b");
        // when
        // then
        assertThat(a).isGreaterThan(b);
    }

    @Test
    public void should_sort_spec_example_correctly() {
        // given
        Version v10 = KubernetesVersionFactory.create("v10");
        Version v2 = KubernetesVersionFactory.create("v2");
        Version v1 = KubernetesVersionFactory.create("v1");
        Version v11beta2 = KubernetesVersionFactory.create("v11beta2");
        Version v10beta3 = KubernetesVersionFactory.create("v10beta3");
        Version v3beta1 = KubernetesVersionFactory.create("v3beta1");
        Version v12alpha1 = KubernetesVersionFactory.create("v12alpha1");
        Version v11alpha2 = KubernetesVersionFactory.create("v11alpha2");
        Version foo1 = KubernetesVersionFactory.create("foo1");
        Version foo10 = KubernetesVersionFactory.create("foo10");
        List<Version> versions = Arrays.asList(
                foo10,
                v11alpha2,
                foo1,
                v3beta1,
                v2,
                v10beta3,
                v11beta2,
                v1,
                v12alpha1,
                v10
        );
        // when
        List<Version> sorted = versions.stream()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        // then
        assertThat(sorted).containsExactly(
                v10,
                v2,
                v1,
                v11beta2,
                v10beta3,
                v3beta1,
                v12alpha1,
                v11alpha2,
                foo1,
                foo10);
    }
}
