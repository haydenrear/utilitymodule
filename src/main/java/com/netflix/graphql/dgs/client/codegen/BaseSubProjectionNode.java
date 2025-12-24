package com.netflix.graphql.dgs.client.codegen;

import java.util.Optional;

public class BaseSubProjectionNode<PARENT extends BaseProjectionNode, ROOT extends BaseProjectionNode>
        extends BaseProjectionNode {

    private final PARENT parent;
    private final ROOT root;
    private final Optional<String> typeName;

    public BaseSubProjectionNode(PARENT parent, ROOT root, Optional<String> typeName) {
        this.parent = parent;
        this.root = root == null ? (ROOT) this : root;
        this.typeName = typeName;
    }

    public PARENT getParent() {
        return parent;
    }

    public ROOT getRoot() {
        return root;
    }

    public PARENT parent() {
        return parent;
    }

    public ROOT root() {
        return root;
    }


    public Optional<String> getTypeName() {
        return typeName;
    }
}
