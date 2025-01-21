package com.connector.common;

@FunctionalInterface
public interface IAuthenticationHandler<Req>
{
    Req authenticate(Req req);
}
