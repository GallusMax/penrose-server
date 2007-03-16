package org.safehaus.penrose.backend;

import org.safehaus.penrose.session.BindRequest;
import com.identyx.javabackend.DN;

/**
 * @author Endi S. Dewata
 */
public class PenroseBindRequest
        extends PenroseRequest
        implements com.identyx.javabackend.BindRequest {

    BindRequest bindRequest;

    public PenroseBindRequest(BindRequest bindRequest) {
        super(bindRequest);
        this.bindRequest = bindRequest;
    }

    public void setDn(String dn) throws Exception {
        bindRequest.setDn(dn);
    }

    public void setDn(DN dn) throws Exception {
        PenroseDN penroseDn = (PenroseDN)dn;
        bindRequest.setDn(penroseDn.getDn());
    }

    public DN getDn() throws Exception {
        return new PenroseDN(bindRequest.getDn());
    }

    public void setPassword(String password) throws Exception {
        bindRequest.setPassword(password);
    }

    public String getPassword() throws Exception {
        return bindRequest.getPassword();
    }

    public BindRequest getBindRequest() {
        return bindRequest;
    }
}
