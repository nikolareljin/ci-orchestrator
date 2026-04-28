def call(Map args = [:]) {
    ciorch([adapter: 'cpp'] + args)
}
