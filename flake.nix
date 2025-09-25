{
  description = "A Nix-flake-based Java development environment";

  inputs.nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1";

  outputs =
    inputs:
    let
      javaVersion = 17; # Change this value to update the whole stack

      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forEachSupportedSystem =
        f:
        inputs.nixpkgs.lib.genAttrs supportedSystems (
          system:
          f {
            pkgs = import inputs.nixpkgs {
              inherit system;
              overlays = [ inputs.self.overlays.default ];
            };
          }
        );
    in
      {
      overlays.default =
        final: prev:
        let
          jdk = prev."jdk${toString javaVersion}";
        in
          {
          inherit jdk;
          maven = prev.maven.override { jdk_headless = jdk; };
          lombok = prev.lombok.override { inherit jdk; };
        };

      devShells = forEachSupportedSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              gcc
              jdk
              maven
              ncurses
              patchelf
              zlib
              gtk3
              glib
              xorg.libX11
              xorg.libXext
              xorg.libXrender
              xorg.libXtst
              xorg.libXi
              xorg.libXcursor
              xorg.libXrandr
              xorg.libXxf86vm
            ];

            shellHook =
              let
                loadLombok = "-javaagent:${pkgs.lombok}/share/java/lombok.jar";
                prev = "\${JAVA_TOOL_OPTIONS:+ $JAVA_TOOL_OPTIONS}";
              in
                ''
                export JAVA_TOOL_OPTIONS="${loadLombok}${prev}"
                export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${pkgs.gtk3}/lib:${pkgs.glib}/lib:${pkgs.xorg.libX11}/lib
                export SHELL="/run/current-system/sw/bin/zsh"
                exec zsh
              '';
          };
        }
      );
    };
}
