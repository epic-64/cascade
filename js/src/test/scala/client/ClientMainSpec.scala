package client

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import client.AppRoute

class ClientMainSpec extends AnyFunSpec with Matchers:

  describe("Client Routing System"):

    describe("Feature: Route to correct application based on pathname"):

      it("should route to Color Rush app when pathname is /color-rush"):
        val pathname = "/color-rush"
        
        val result = routeFromPathname(pathname)
        
        result shouldBe AppRoute.ColorRush

      it("should route to Counter app when pathname is /counter"):
        val pathname = "/counter"
        
        val result = routeFromPathname(pathname)
        
        result shouldBe AppRoute.Counter

      it("should route to landing page when pathname is /"):
        val pathname = "/"
        
        val result = routeFromPathname(pathname)
        
        result shouldBe AppRoute.Landing

      it("should route to landing page when pathname is unknown"):
        val pathname = "/unknown-page"
        
        val result = routeFromPathname(pathname)
        
        result shouldBe AppRoute.Landing

      it("should route to landing page when pathname is /about"):
        val pathname = "/about"
        
        val result = routeFromPathname(pathname)
        
        result shouldBe AppRoute.Landing

  describe("Feature: Safe initialization logic"):

    it("should defer initialization when document state is loading"):
      val documentState = "loading"
      
      val shouldDefer = shouldDeferInit(documentState)
      
      shouldDefer shouldBe true

    it("should execute immediately when document state is interactive"):
      val documentState = "interactive"
      
      val shouldDefer = shouldDeferInit(documentState)
      
      shouldDefer shouldBe false

    it("should execute immediately when document state is complete"):
      val documentState = "complete"
      
      val shouldDefer = shouldDeferInit(documentState)
      
      shouldDefer shouldBe false


