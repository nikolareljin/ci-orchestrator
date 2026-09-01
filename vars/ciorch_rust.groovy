def call(Map args = [:]) {
    ciorch(args + [adapter: 'rust', matrix: 'rust-standard'])
}
