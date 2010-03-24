// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.api.data.finance.v2.atom;

import com.google.api.data.client.v2.atom.NamespaceDictionary;

public final class FinanceAtom {

  public static final NamespaceDictionary NAMESPACE_DICTIONARY;
  static {
    NamespaceDictionary.Builder builder = new NamespaceDictionary.Builder();
    builder.addNamespace("", "http://www.w3.org/2005/Atom");
    builder.addNamespace("app", "http://www.w3.org/2007/app");
    builder.addNamespace("atom", "http://www.w3.org/2005/Atom");
    builder.addNamespace("gd", "http://schemas.google.com/g/2005");
    builder.addNamespace("gf", "http://schemas.google.com/finance/2007");
    builder.addNamespace("openSearch", "http://a9.com/-/spec/opensearch/1.1/");
    builder.addNamespace("xml", "http://www.w3.org/XML/1998/namespace");
    NAMESPACE_DICTIONARY = builder.build();
  }

  private FinanceAtom() {
  }
}
