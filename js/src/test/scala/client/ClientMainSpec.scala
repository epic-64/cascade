package client

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import client.{AppRoute, GameRoute}

class ClientMainSpec extends AnyFunSpec with Matchers:

  describe("Client Routing System"):

    describe("Feature: Route to correct application based on pathname"):

      it("should route to Color Rush app when pathname is /color-rush"):
        val pathname = "/color-rush"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.ColorRush, None)

      it("should route to Color Rush with lobby ID when pathname is /color-rush/ABC123"):
        val pathname = "/color-rush/ABC123"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.ColorRush, Some("ABC123"))

      it("should uppercase lobby ID from pathname"):
        val pathname = "/color-rush/abc123"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.ColorRush, Some("ABC123"))

      it("should route to AI Drawing with lobby ID"):
        val pathname = "/ai-drawing/XYZ789"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.AIDrawing, Some("XYZ789"))

      it("should route to Tug of War with lobby ID"):
        val pathname = "/tug-of-war/DEF456"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.TugOfWar, Some("DEF456"))

      it("should route to Counter app when pathname is /counter"):
        val pathname = "/counter"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.Counter, None)

      it("should route to landing page when pathname is /"):
        val pathname = "/"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.Landing, None)

      it("should route to landing page when pathname is unknown"):
        val pathname = "/unknown-page"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.Landing, None)

      it("should route to landing page when pathname is /about"):
        val pathname = "/about"
        
        val result = parseRoute(pathname)
        
        result shouldBe GameRoute(AppRoute.Landing, None)

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


